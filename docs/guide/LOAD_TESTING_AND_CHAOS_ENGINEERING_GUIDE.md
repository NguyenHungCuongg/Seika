# Hướng dẫn Load Testing, Rate Limit Verification & Chaos Engineering

Tài liệu này hướng dẫn cách kiểm chứng các cơ chế chịu tải đã triển khai trong hệ thống Seika: **Rate Limiting**, **Circuit Breaker**, và **hiệu năng tổng thể** bằng Grafana k6 kết hợp Observability Stack.

## Mục lục

1. [Chuẩn bị môi trường](#1-chuẩn-bị-môi-trường)
2. [Tổng quan kịch bản test](#2-tổng-quan-kịch-bản-test)
3. [Cách chạy Load Test](#3-cách-chạy-load-test)
4. [Cách chạy Chaos Test (Circuit Breaker)](#4-cách-chạy-chaos-test-circuit-breaker)
5. [Đọc kết quả và phân tích Bottleneck](#5-đọc-kết-quả-và-phân-tích-bottleneck)
6. [Kịch bản demo báo cáo đồ án](#6-kịch-bản-demo-báo-cáo-đồ-án)

---

## 1. Chuẩn bị môi trường

### 1.1. Khởi động hệ thống

```powershell
# Khởi động toàn bộ backend + infrastructure
docker compose up -d

# Khởi động Observability Stack (Prometheus, Grafana, Loki, Tempo)
docker compose -f docker-compose.observability.yml up -d
```

### 1.2. Kiểm tra containers

```powershell
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

Đảm bảo các container sau đang `Up`:

| Container                   | Port | Vai trò                     |
| --------------------------- | ---- | --------------------------- |
| `seika-api-gateway-1`       | 8080 | API Gateway + Rate Limiting |
| `seika-identity-service-1`  | 8081 | Auth + JWT                  |
| `seika-flashcard-service-1` | 8086 | Flashcard + Feign → Wallet  |
| `seika-wallet-service-1`    | 8084 | Wallet (target cho Chaos)   |
| `seika-redis-1`             | 6379 | Rate Limit counter store    |
| `grafana`                   | 3000 | Dashboard giám sát          |
| `prometheus`                | 9090 | Metrics scraping            |
| `tempo`                     | 3200 | Distributed Tracing         |

### 1.3. Cài đặt k6

**Cách 1: Dùng Docker (không cần cài)**

k6 sẽ chạy qua Docker image `grafana/k6`. Không cần cài thêm gì.

**Cách 2: Cài trực tiếp trên Windows**

```powershell
winget install Grafana.k6
```

---

## 2. Tổng quan kịch bản test

File test: [`scripts/load-test.js`](../../scripts/load-test.js)

Kịch bản được thiết kế với **3 Scenarios chạy song song**, mỗi cái kiểm chứng một khía cạnh khác nhau của hệ thống:

### Scenario 1: `normal_load` — Tải thông thường

Mô phỏng người dùng thực: đăng nhập → xem Flashcard.

```
  Virtual Users (VUs)
  50 |                    +------+ (Peak: 50 VUs)
     |                   /        \
  30 |        +----------+          + (Cool-down)
     |       /  (Normal: 30 VUs)     \
  10 |      +                         +
     |     / (Warm-up)                 \
   0 +----+------+------+------+-------+---> Thời gian
     0s   20s    60s    80s    100s
```

**Mục đích:** Đánh giá p95/p99 latency và throughput dưới tải bình thường.

### Scenario 2: `rate_limit_test` — Kiểm chứng Rate Limiting

Bắn **50 requests/giây** liên tục vào `POST /api/auth/login` trong 30 giây.

Cấu hình Rate Limit hiện tại cho `/api/auth/**`:

- `replenishRate`: 10 token/giây
- `burstCapacity`: 20 token

**Kỳ vọng:** Khoảng 20 request đầu thành công (burst), sau đó khoảng 80% request bị trả về **HTTP 429 Too Many Requests**.

**Metric kiểm chứng:**

- `rate_limited` > 0% → Rate Limiter đang hoạt động
- `rate_limit_429_total` → Tổng số request bị chặn

### Scenario 3: `circuit_breaker_probe` — Dò Circuit Breaker

Gọi liên tục vào `/actuator/health` và `/api/flashcards` (endpoint phụ thuộc Wallet qua Feign) với 5 VUs trong 100 giây.

**Mục đích:** Khi kết hợp với script `chaos-test.ps1` (tắt Wallet Service), scenario này cho thấy:

- Hệ thống **vẫn phản hồi** khi downstream service chết (nhờ Fallback)
- Thời gian phản hồi **không tăng lên 60 giây** (nhờ Circuit Breaker ngắt mạch)

---

## 3. Cách chạy Load Test

### Chạy bằng Docker

```powershell
Get-Content scripts\load-test.js | docker run --rm -i `
  -e BASE_URL="http://host.docker.internal:8080" `
  grafana/k6 run -
```

### Chạy bằng k6 CLI

```powershell
k6 run scripts\load-test.js
```

### Tùy chỉnh biến môi trường

```powershell
# Đổi target URL
k6 run -e BASE_URL="http://192.168.1.100:8080" scripts\load-test.js
```

---

## 4. Cách chạy Chaos Test (Circuit Breaker)

File script: [`scripts/chaos-test.ps1`](../../scripts/chaos-test.ps1)

Script này tự động hóa toàn bộ quy trình: **chạy k6 → chờ warm-up → tắt Wallet Service → quan sát → bật lại → xem kết quả**.

### Chạy lệnh

```powershell
.\scripts\chaos-test.ps1
```

### Các tham số tùy chỉnh

```powershell
.\scripts\chaos-test.ps1 `
  -WalletContainer "seika-wallet-service" `
  -WarmupSeconds 15 `
  -ChaosSeconds 30 `
  -RecoverySeconds 20
```

### Quy trình 5 Phase tự động

```
Phase 1                Phase 2            Phase 3                   Phase 4            Phase 5
K6 khởi động    →    Warm-up 15s    →    CHAOS: Wallet bị pause  →  Wallet unpause  →  Xem kết quả
(background)         (ổn định)           (30s quan sát CB)          (phục hồi)         (k6 summary)
```

### Dấu hiệu Circuit Breaker hoạt động

| Chỉ số              | Nếu CB KHÔNG hoạt động         | Nếu CB ĐANG hoạt động               |
| ------------------- | ------------------------------ | ----------------------------------- |
| `http_req_duration` | Vọt lên 5000-60000ms (timeout) | Vẫn giữ < 100ms (fallback trả ngay) |
| `errors`            | Tăng vọt > 50%                 | Tăng nhẹ 1-2s rồi ổn định           |
| Gateway health      | 502/503 liên tục               | Vẫn trả 200 OK                      |
| Tomcat thread pool  | Cạn kiệt (blocked)             | Bình thường                         |

---

## 5. Đọc kết quả và phân tích Bottleneck

### 5.1. Đọc kết quả trên Console K6

Khi test kết thúc, k6 hiển thị bảng tổng kết. Chú ý vào:

```
  http_req_duration..........: avg=120ms  min=5ms  med=80ms  max=4500ms  p(90)=300ms  p(95)=500ms
  errors.....................: 3.20%  ✓ 48  ✗ 1452
  rate_limited...............: 62.50%  ✓ 562  ✗ 338
  rate_limit_429_total.......: 562
```

**Cách đọc:**

- `p(95) < 1000ms` → PASS (95% request xong dưới 1 giây)
- `errors < 10%` → PASS
- `rate_limited > 0%` → PASS (Rate Limiter đang chặn request thừa)
- `rate_limit_429_total = 562` → Có 562 request bị từ chối bởi Redis Token Bucket

### 5.2. Khoanh vùng Bottleneck trên Grafana

1. Truy cập **http://localhost:3000** (admin / admin)
2. Mở dashboard **Spring Boot APM** hoặc **JVM Micrometer**
3. Kiểm tra:

| Chỉ số                   | Ngưỡng nguy hiểm    | Ý nghĩa                         |
| ------------------------ | ------------------- | ------------------------------- |
| CPU Usage                | > 80%               | Service đang quá tải xử lý      |
| JVM Heap Memory          | > 90% + GC liên tục | Leak hoặc tạo quá nhiều objects |
| HikariCP Pending Threads | > 0 kéo dài         | Hết connection pool xuống DB    |
| Tomcat Active Threads    | = Max Threads       | Thread pool cạn kiệt            |

### 5.3. Tìm nguyên nhân gốc rễ bằng Tempo Tracing

1. Vào Grafana → **Explore** → chọn Data Source **Tempo**
2. Tab **Search** → nhập **Min Duration**: `1000ms` → **Run query**
3. Bấm vào TraceID để mở biểu đồ **Waterfall**

| Hiện tượng trên Waterfall                      | Nguyên nhân                                    | Giải pháp                                       |
| ---------------------------------------------- | ---------------------------------------------- | ----------------------------------------------- |
| Thanh dài ở query DB (`SELECT`, `find`)        | Thiếu Index hoặc N+1 Query                     | Thêm Index, dùng JOIN/Eager Loading             |
| Thanh dài ở Feign call (`lb://WALLET-SERVICE`) | Downstream chậm, không có Circuit Breaker      | Đã khắc phục bằng Resilience4j Fallback         |
| Thanh dài ở Gateway nhưng downstream ngắn      | Filter chain nặng (Security, CORS, Rate Limit) | Tối ưu WebFlux filter, cache token verification |
| Thanh dài ở `authorize` / Security             | Bcrypt work factor quá cao dưới tải đồng thời  | Giảm work factor hoặc cache session             |

---

## 6. Kịch bản demo báo cáo đồ án

Khi trình bày trước giáo viên, có thể thực hiện theo 4 bước:

### Bước 1: Giới thiệu (30 giây)

> _"Để kiểm chứng kiến trúc Microservices không chỉ chạy đúng nghiệp vụ mà còn chịu được tải cao và tự phục hồi khi có sự cố, em sử dụng Grafana k6 để ép tải và kết hợp kỹ thuật Chaos Engineering."_

### Bước 2: Chạy Load Test + Chaos (2 phút)

```powershell
.\scripts\chaos-test.ps1
```

Mở Terminal chia đôi màn hình:

- **Bên trái**: Terminal đang chạy script (hiển thị các Phase)
- **Bên phải**: Grafana Dashboard (http://localhost:3000)

### Bước 3: Giải thích khi Wallet bị tắt (1 phút)

> _"Ở Phase 3, em chủ động tắt Wallet Service để mô phỏng sự cố. Nhờ Circuit Breaker (Resilience4j), thay vì toàn bộ hệ thống bị treo 60 giây chờ timeout, Circuit Breaker ngắt mạch sau 5 request lỗi và trả về Fallback ngay lập tức. Các chức năng khác như Login, xem Flashcard vẫn hoạt động bình thường."_

### Bước 4: Chỉ vào kết quả Rate Limiting (30 giây)

> _"Ngoài ra, em cũng kiểm chứng Rate Limiting: khi bắn 30 request/giây vào endpoint đăng nhập (vượt ngưỡng 10 req/s), hệ thống tự động trả HTTP 429 cho các request thừa. Metric `rate_limit_429_total` trên k6 cho thấy có [X] request đã bị chặn thành công, chống được tấn công brute-force."_
