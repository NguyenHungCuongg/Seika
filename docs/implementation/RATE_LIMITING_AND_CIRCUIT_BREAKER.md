# Triển khai Rate Limiting & Circuit Breaker cho Seika Microservices

## Mục lục

1. [Bối cảnh vấn đề](#1-bối-cảnh-vấn-đề)
2. [Phân tích rủi ro](#2-phân-tích-rủi-ro)
3. [Giải pháp 1: Rate Limiting tại API Gateway](#3-giải-pháp-1-rate-limiting-tại-api-gateway)
4. [Giải pháp 2: Circuit Breaker cho Inter-Service Calls](#4-giải-pháp-2-circuit-breaker-cho-inter-service-calls)
5. [Tổng kết các file đã thay đổi](#5-tổng-kết-các-file-đã-thay-đổi)

---

## 1. Bối cảnh vấn đề

Seika là hệ thống microservices gồm 8 business services giao tiếp với nhau thông qua REST (OpenFeign) và message broker (RabbitMQ). Toàn bộ traffic từ client đi qua một **API Gateway** duy nhất trước khi được route tới các service phía sau.

```
Client (React SPA)
       │
       ▼
 API Gateway (:8080)
       │
       ├── Identity Service   ──Feign──▶ Profile Service
       │                      ──Feign──▶ Wallet Service
       │                      ──Feign──▶ Marketplace Service
       ├── Flashcard Service  ──Feign──▶ Wallet Service
       ├── Quiz Service       ──Feign──▶ Wallet Service
       ├── Marketplace Service
       ├── Wallet Service
       ├── Profile Service
       ├── Notification Service
       └── Reward Service
```

Qua quá trình review hệ thống, hai thiếu sót nghiêm trọng đã được phát hiện:

### 1.1. Không có Rate Limiting

Hệ thống **hoàn toàn không giới hạn số lượng request** mà một client có thể gửi đến API. Không có `RequestRateLimiter`, không có `Bucket4j`, không có bất kỳ cơ chế throttling nào — kể cả ở Gateway lẫn từng service riêng lẻ.

### 1.2. Không có Circuit Breaker

Các Feign client gọi liên service **không có fallback, không có circuit breaker, không có timeout hợp lý**. Feign mặc định chờ ~60 giây cho mỗi request. Nếu service đích bị sập, service gọi sẽ treo thread trong 60 giây đó, và nếu nhiều request cùng đến, thread pool sẽ cạn kiệt.

---

## 2. Phân tích rủi ro

### 2.1. Brute-force & API Abuse (do thiếu Rate Limiting)

Khi không có giới hạn, hacker có thể:

| Kịch bản tấn công         | Endpoint bị nhắm                | Hậu quả                                                         |
| ------------------------- | ------------------------------- | --------------------------------------------------------------- |
| Dò mật khẩu (Brute-force) | `POST /api/auth/login`          | Thử hàng nghìn mật khẩu/giây cho đến khi tìm được mật khẩu đúng |
| Spam tạo tài khoản rác    | `POST /api/auth/register`       | Database phình to với tài khoản giả, Wallet bị tạo hàng loạt    |
| Cào dữ liệu (Scraping)    | `GET /api/marketplace/**`       | Toàn bộ nội dung khóa học bị sao chép                           |
| Flood SSE connections     | `GET /api/notifications/stream` | RAM server cạn kiệt do `ConcurrentHashMap` phình to             |

### 2.2. Cascading Failure (do thiếu Circuit Breaker)

Đây là kịch bản nguy hiểm nhất — một service nhỏ bị sập kéo theo sập toàn bộ hệ thống:

```
Bước 1: Wallet Service bị sập (OOM, DB timeout, v.v.)
            │
Bước 2: Identity Service gọi Wallet qua Feign
         → Feign chờ 60s timeout
         → Thread bị block
         → 100 users đăng ký cùng lúc = 100 threads bị block
            │
Bước 3: Identity Service cạn kiệt thread pool
         → Không xử lý được login/register/refresh
         → Identity Service "chết" theo
            │
Bước 4: API Gateway gọi Identity → timeout
         → Gateway connections tích tụ
         → TOÀN BỘ API CHẾT
```

Điểm mấu chốt: **Wallet Service chỉ phục vụ tính năng phụ** (hiển thị tổng lưu thông trên admin dashboard), nhưng khi nó sập lại kéo theo cả tính năng đăng nhập/đăng ký — hai tính năng cốt lõi nhất của hệ thống.

---

## 3. Giải pháp 1: Rate Limiting tại API Gateway

### 3.1. Lựa chọn công nghệ

Spring Cloud Gateway tích hợp sẵn filter `RequestRateLimiter` sử dụng thuật toán **Token Bucket** với Redis làm backend lưu trữ. Hệ thống Seika đã có sẵn Redis (cho JWT blacklist + caching), nên không cần thêm infrastructure mới.

**Token Bucket hoạt động như sau:**

```
Bucket (dung lượng = burstCapacity)
┌──────────────────────────────────┐
│ ●  ●  ●  ●  ●  ●  ●  ●  ●  ●  │  ← Mỗi ● là 1 token
└──────────────────────────────────┘
  ▲                           │
  │ Nạp thêm token            │ Mỗi request lấy đi 1 token
  │ (replenishRate/giây)       ▼
                          Request được xử lý

Khi bucket hết token → Request bị từ chối (HTTP 429)
```

Hai tham số chính:

- **`replenishRate`**: Số token được nạp vào bucket mỗi giây (= tốc độ xử lý ổn định).
- **`burstCapacity`**: Dung lượng tối đa của bucket (= số request đột biến tối đa được chấp nhận).

### 3.2. Implementation

#### Bước 1: Tạo Key Resolvers

Key Resolver xác định **"ai" đang gửi request** để áp rate limit riêng cho từng người/IP.

File: `src/api-gateway/src/main/java/com/seika/api_gateway/config/RateLimiterConfig.java`

```java
@Configuration
public class RateLimiterConfig {

    // Key Resolver chính: ưu tiên User ID, fallback sang IP
    @Primary
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // X-User-Id đã được AuthenticationFilter parse từ JWT và inject vào header
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isEmpty()) {
                return Mono.just(userId);  // Rate limit theo user
            }
            // Nếu chưa đăng nhập, dùng IP
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            return Mono.just(remoteAddress != null
                    ? remoteAddress.getAddress().getHostAddress()
                    : "unknown-ip");
        };
    }

    // Key Resolver phụ: luôn dùng IP (cho các endpoint public)
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            return Mono.just(remoteAddress != null
                    ? remoteAddress.getAddress().getHostAddress()
                    : "unknown-ip");
        };
    }
}
```

**Tại sao cần 2 resolver?**

- `userKeyResolver`: Cho các API đã xác thực — mỗi **user** có quota riêng. User A gửi 50 req/s không ảnh hưởng tới User B.
- `ipKeyResolver`: Cho các API public (`/api/auth/login`, `/api/auth/register`) — lúc này chưa có user ID nên dùng IP address.

#### Bước 2: Cấu hình trên Gateway Routes

File: `src/config-service/src/main/resources/configs/api-gateway.yaml`

**Global Default Filter** — áp dụng cho tất cả routes:

```yaml
spring:
  cloud:
    gateway:
      default-filters:
        - name: RequestRateLimiter
          args:
            redis-rate-limiter.replenishRate: 50 # 50 token/giây
            redis-rate-limiter.burstCapacity: 100 # Tối đa 100 token trong bucket
            key-resolver: "#{@userKeyResolver}" # Giới hạn theo user/IP
```

**Route-specific Filter** — siết chặt hơn cho `/api/auth/**`:

```yaml
- id: identity-auth-route
  uri: lb://IDENTITY-SERVICE
  predicates:
    - Path=/api/auth/**
  filters:
    - name: RequestRateLimiter
      args:
        redis-rate-limiter.replenishRate: 10 # 10 token/giây
        redis-rate-limiter.burstCapacity: 20 # Burst tối đa 20
        key-resolver: "#{@ipKeyResolver}" # Theo IP (chưa đăng nhập)
```

**Ý nghĩa thực tế:**

- API thông thường: Mỗi user được gửi tối đa **50 request/giây** ổn định, cho phép burst lên **100 request** trong tích tắc.
- API đăng nhập/đăng ký: Mỗi IP chỉ được **10 request/giây**, burst tối đa **20** — đủ cho người dùng bình thường nhưng chặn được tool brute-force.

### 3.3. Luồng xử lý khi bị rate limit

```
Client gửi request
       │
       ▼
 ┌─ API Gateway ─────────────────────────────────┐
 │                                                │
 │  1. RequestRateLimiter Filter                  │
 │     ├── Lấy key từ KeyResolver (userId/IP)     │
 │     ├── Gọi Redis: EVALSHA (Lua script)        │
 │     │   └── Kiểm tra & trừ token               │
 │     │                                          │
 │     ├── Còn token? ──▶ Tiếp tục filter chain   │
 │     └── Hết token?  ──▶ HTTP 429 + Headers:    │
 │                          X-RateLimit-Remaining  │
 │                          X-RateLimit-Burst      │
 │                          X-RateLimit-Replenish  │
 │                                                │
 │  2. AuthenticationFilter (nếu đã qua bước 1)  │
 │  3. Route tới downstream service               │
 └────────────────────────────────────────────────┘
```

---

## 4. Giải pháp 2: Circuit Breaker cho Inter-Service Calls

### 4.1. Lựa chọn công nghệ

Sử dụng **Resilience4j** — thư viện fault-tolerance chuẩn cho Java, tích hợp native với Spring Cloud OpenFeign thông qua `spring-cloud-starter-circuitbreaker-resilience4j`.

### 4.2. Circuit Breaker hoạt động như thế nào?

```
              ┌──────────────────────────────────────────┐
               │                  CLOSED                  │
               │   (Trạng thái BÌNH THƯỜNG - Mở cửa)     │
               └────────────────────┬─────────────────────┘
                                    │
                                    │ Tỉ lệ lỗi ≥ 50%
                                    │ (Ví dụ: 5/10 request gần nhất bị lỗi)
                                    ▼
               ┌──────────────────────────────────────────┐
               │                   OPEN                   │
               │   (NGẮT MẠCH - Chặn request -> Fallback) │
               └────────────────────┬─────────────────────┘
                                    │
                                    │ Sau 30 giây chờ (Wait Duration)
                                    ▼
               ┌──────────────────────────────────────────┐
               │                HALF-OPEN                 │
               │   (THỬ NGHIỆM - Cho 3 request kiểm tra) │
               └──────┬────────────────────────────┬──────┘
                      │                            │
      Cả 3 request    │                            │ Có ít nhất 1 request
      THÀNH CÔNG      │                            │ THẤT BẠI
                      ▼                            ▼
         ┌─────────────────────────┐    ┌─────────────────────────┐
         │     Chuyển về CLOSED    │    │     Quay lại OPEN       │
         │  (Hệ thống đã phục hồi) │    │   (Hệ thống vẫn chết)   │
         └─────────────────────────┘    └─────────────────────────┘
```

**Giải thích các tham số đã cấu hình:**

| Tham số                                        | Giá trị | Ý nghĩa                                          |
| ---------------------------------------------- | ------- | ------------------------------------------------ |
| `sliding-window-size`                          | 10      | Đánh giá tỉ lệ lỗi trên 10 request gần nhất      |
| `failure-rate-threshold`                       | 50%     | Nếu ≥ 5/10 request lỗi → mở circuit              |
| `wait-duration-in-open-state`                  | 30s     | Chờ 30s trước khi thử lại (HALF-OPEN)            |
| `permitted-number-of-calls-in-half-open-state` | 3       | Cho phép 3 request thử trong HALF-OPEN           |
| `minimum-number-of-calls`                      | 5       | Cần ít nhất 5 request trước khi bắt đầu đánh giá |

### 4.3. Implementation

#### Bước 1: Thêm dependency

Thêm vào `pom.xml` của **identity-service**, **flashcard-service**, và **quiz-service**:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
```

#### Bước 2: Cấu hình YAML

Thêm vào config YAML của mỗi service (qua Config Server):

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 3000 # Tối đa 3s để mở kết nối (trước đó: 60s mặc định)
            read-timeout: 5000 # Tối đa 5s để đọc response  (trước đó: 60s mặc định)
      circuitbreaker:
        enabled: true # Bật Circuit Breaker cho Feign

resilience4j:
  circuitbreaker:
    configs:
      default:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        minimum-number-of-calls: 5
  timelimiter:
    configs:
      default:
        timeout-duration: 5s # Hard timeout bọc ngoài Feign
```

#### Bước 3: Tạo Fallback classes

Mỗi Feign client interface được gắn một lớp `fallback` — lớp này implement cùng interface và chứa logic xử lý khi service đích không khả dụng.

**Ví dụ: Identity Service gọi Wallet Service**

Feign Client (trước):

```java
@FeignClient(name = "wallet-service", url = "...", configuration = FeignClientConfig.class)
public interface WalletClient {
    @GetMapping("/api/wallet/admin/total-circulation")
    String getTotalCirculation();
}
```

Feign Client (sau):

```java
@FeignClient(name = "wallet-service", url = "...", configuration = FeignClientConfig.class,
             fallback = WalletClientFallback.class)  // ← Thêm fallback
public interface WalletClient {
    @GetMapping("/api/wallet/admin/total-circulation")
    String getTotalCirculation();
}
```

Fallback class:

```java
@Component
@Slf4j
public class WalletClientFallback implements WalletClient {
    @Override
    public String getTotalCirculation() {
        log.warn("Wallet service is unavailable. Returning fallback N/A.");
        return "N/A";  // Admin dashboard hiển thị "N/A" thay vì crash
    }
}
```

### 4.4. Chiến lược Fallback cho từng service

Không phải mọi fallback đều trả giá trị mặc định. Tùy vào mức độ quan trọng của thao tác, fallback sẽ xử lý khác nhau:

| Service       | Feign Client      | Method                   | Fallback Strategy          | Lý do                                                    |
| ------------- | ----------------- | ------------------------ | -------------------------- | -------------------------------------------------------- |
| **Identity**  | ProfileClient     | `createProfile()`        | **Throw RuntimeException** | Profile là bắt buộc khi đăng ký — không thể bỏ qua       |
| **Identity**  | WalletClient      | `getTotalCirculation()`  | Trả `"N/A"`                | Chỉ hiển thị trên admin dashboard — không ảnh hưởng user |
| **Identity**  | MarketplaceClient | `countPendingProducts()` | Trả `0L`                   | Chỉ hiển thị trên admin dashboard                        |
| **Flashcard** | WalletClient      | `spend()` / `deposit()`  | **Throw RuntimeException** | Giao dịch tiền không được bỏ qua                         |
| **Flashcard** | WalletClient      | `getConfigs()`           | Trả `List.of()`            | Config hệ thống — dùng giá trị mặc định                  |
| **Quiz**      | WalletClient      | `getConfigs()`           | Trả `List.of()`            | Config hệ thống — dùng giá trị mặc định                  |

**Nguyên tắc:**

- **Thao tác đọc/hiển thị** (dashboard, config) → Trả giá trị mặc định an toàn, hệ thống tiếp tục hoạt động.
- **Thao tác ghi/thanh toán** (tạo profile, rút tiền, nạp tiền) → Throw exception rõ ràng, thông báo lỗi cho user, **không bao giờ bỏ qua giao dịch tiền**.

### 4.5. So sánh trước và sau

**Trước khi có Circuit Breaker:**

```
Wallet Service sập
  → Identity gọi Feign, chờ 60s timeout
  → 100 requests đồng thời = 100 threads bị block 60s
  → Identity thread pool cạn kiệt
  → Login/Register API chết
  → Toàn bộ hệ thống chết
  → Thời gian phục hồi: Phải restart thủ công
```

**Sau khi có Circuit Breaker:**

```
Wallet Service sập
  → 5 request đầu tiên fail (minimum-number-of-calls)
  → Circuit Breaker mở (OPEN state)
  → Các request tiếp theo trả fallback NGAY LẬP TỨC (0ms thay vì 60s)
  → Identity Service vẫn hoạt động bình thường cho login/register
  → Admin dashboard hiển thị "N/A" cho Wallet data
  → Sau 30s, Circuit Breaker tự thử lại (HALF-OPEN)
  → Wallet Service phục hồi → Circuit tự đóng (CLOSED)
  → Thời gian phục hồi: TỰ ĐỘNG
```

---

## 5. Tổng kết các file đã thay đổi

### API Gateway — Rate Limiting

| File                                                | Hành động     | Mô tả                                                                          |
| --------------------------------------------------- | ------------- | ------------------------------------------------------------------------------ |
| `src/api-gateway/.../config/RateLimiterConfig.java` | **Tạo mới**   | 2 KeyResolver beans (theo user/IP)                                             |
| `src/config-service/.../configs/api-gateway.yaml`   | **Chỉnh sửa** | Thêm `default-filters` (50 req/s) + route filter cho `/api/auth/**` (10 req/s) |

### Identity Service — Circuit Breaker

| File                                                               | Hành động     | Mô tả                                                   |
| ------------------------------------------------------------------ | ------------- | ------------------------------------------------------- |
| `src/services/identity-service/pom.xml`                            | **Chỉnh sửa** | Thêm `spring-cloud-starter-circuitbreaker-resilience4j` |
| `src/config-service/.../configs/identity-service.yaml`             | **Chỉnh sửa** | Thêm Feign timeout (3s/5s) + Resilience4j config        |
| `src/services/identity-service/.../ProfileClientFallback.java`     | **Tạo mới**   | Throw exception khi Profile Service sập                 |
| `src/services/identity-service/.../WalletClientFallback.java`      | **Tạo mới**   | Trả "N/A" khi Wallet Service sập                        |
| `src/services/identity-service/.../MarketplaceClientFallback.java` | **Tạo mới**   | Trả 0L khi Marketplace Service sập                      |
| `src/services/identity-service/.../ProfileClient.java`             | **Chỉnh sửa** | Gắn `fallback = ProfileClientFallback.class`            |
| `src/services/identity-service/.../WalletClient.java`              | **Chỉnh sửa** | Gắn `fallback = WalletClientFallback.class`             |
| `src/services/identity-service/.../MarketplaceClient.java`         | **Chỉnh sửa** | Gắn `fallback = MarketplaceClientFallback.class`        |

### Flashcard Service — Circuit Breaker

| File                                                           | Hành động     | Mô tả                                                   |
| -------------------------------------------------------------- | ------------- | ------------------------------------------------------- |
| `src/services/flashcard-service/pom.xml`                       | **Chỉnh sửa** | Thêm `spring-cloud-starter-circuitbreaker-resilience4j` |
| `src/config-service/.../configs/flashcard-service.yaml`        | **Chỉnh sửa** | Giảm Feign timeout (10s → 3s/5s) + Resilience4j config  |
| `src/services/flashcard-service/.../WalletClientFallback.java` | **Tạo mới**   | Fallback cho 3 methods                                  |
| `src/services/flashcard-service/.../WalletClient.java`         | **Chỉnh sửa** | Gắn `fallback = WalletClientFallback.class`             |

### Quiz Service — Circuit Breaker

| File                                                      | Hành động     | Mô tả                                                   |
| --------------------------------------------------------- | ------------- | ------------------------------------------------------- |
| `src/services/quiz-service/pom.xml`                       | **Chỉnh sửa** | Thêm `spring-cloud-starter-circuitbreaker-resilience4j` |
| `src/config-service/.../configs/quiz-service.yaml`        | **Chỉnh sửa** | Thêm Feign timeout (3s/5s) + Resilience4j config        |
| `src/services/quiz-service/.../WalletClientFallback.java` | **Tạo mới**   | Fallback cho `getConfigs()`                             |
| `src/services/quiz-service/.../WalletClient.java`         | **Chỉnh sửa** | Gắn `fallback = WalletClientFallback.class`             |
