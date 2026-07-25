# Design Patterns trong dự án Seika — Phân tích chi tiết

> Tài liệu này liệt kê và phân tích **đầy đủ các design pattern** đang được áp dụng (và ý-thức-không-áp-dụng) trong hệ thống microservices của Seika. Mỗi pattern có bằng chứng cụ thể: tên class, đường dẫn file, đoạn code trích, và lý do áp dụng.
>
> Stack: **Spring Boot 4.0.4**, **Spring Cloud 2025.1.1**, **Java 21**, **Postgres 16**, **MongoDB 7**, **RabbitMQ 4.3**, **Netflix Eureka**, **Spring Cloud Gateway WebFlux**, **MapStruct 1.6.3**, **Lombok**, **JWT (jjwt 0.13.0)**.

---

## Mục lục

| #   | Pattern                                                                    | Trạng thái                   |
| --- | -------------------------------------------------------------------------- | ---------------------------- |
| 1   | [Saga (Choreography vs Orchestration)](#1-saga-pattern--choreography-only) | Dùng một phần (choreography) |
| 2   | [Transactional Outbox](#2-transactional-outbox-pattern--dùng--2-nơi)       | Dùng (×2)                    |
| 3   | [Inbox (Idempotent Receiver)](#3-inbox-pattern--dùng)                      | Dùng                         |
| 4   | [Layered Architecture](#4-layered-architecture--dùng)                      | Dùng (toàn hệ thống)         |
| 5   | [Hexagonal / Ports & Adapters](#5-hexagonal--ports--adapters--không-dùng)  | **KHÔNG** dùng               |
| 6   | [CQRS](#6-cqrs--không-dùng)                                                | **KHÔNG** dùng               |
| 7   | [Event Sourcing](#7-event-sourcing--không-dùng)                            | **KHÔNG** dùng               |
| 8   | [API Gateway](#8-api-gateway-pattern--dùng)                                | Dùng                         |
| 9   | [Service Registry / Discovery](#9-service-registry--discovery--dùng)       | Dùng                         |
| 10  | [Config Server](#10-config-server-pattern--dùng)                           | Dùng                         |
| 11  | [Database per Service](#11-database-per-service--dùng)                     | Dùng                         |
| 12  | [Circuit Breaker](#12-circuit-breaker--không-dùng)                         | **KHÔNG** dùng               |
| 13  | [Bulkhead](#13-bulkhead-pattern--không-dùng)                               | **KHÔNG** dùng               |
| 14  | [MapStruct](#14-mapstruct--dùng)                                           | Dùng                         |
| 15  | [Repository](#15-repository-pattern--dùng)                                 | Dùng                         |
| 16  | [Builder](#16-builder-pattern--dùng)                                       | Dùng (Lombok)                |
| 17  | [Strategy](#17-strategy-pattern--không-dùng-cổ-điển)                       | Đa hình sealed interface     |
| 18  | [Observer / Pub-Sub (Message Broker)](#18-observer--pub-sub--dùng)         | Dùng (RabbitMQ)              |
| 19  | [Factory](#19-factory-pattern--không-dùng)                                 | **KHÔNG** dùng               |
| 20  | [Decorator](#20-decorator-pattern--không-dùng-oo)                          | Filter SPI                   |
| 21  | [Dependency Injection](#21-dependency-injection--dùng)                     | Dùng                         |
| 22  | [Idempotency](#22-idempotency-patterns--dùng)                              | Dùng                         |
| 23  | [Dead Letter Queue](#23-dead-letter-queue--không-cấu-hình)                 | **KHÔNG**                    |
| 24  | [Outbox + Choreography](#24-outbox--choreography--kết-hợp)                 | Đã trình bày ở §1, §2        |
| 25  | [Strangler Fig](#25-strangler-fig-pattern--không-dùng)                     | **KHÔNG**                    |
| 26  | [Sidecar](#26-sidecar-pattern--không-dùng-trong-code)                      | **KHÔNG** trong app code     |
| 27  | [BFF (Backend for Frontend)](#27-bff-backend-for-frontend--không-dùng)     | **KHÔNG**                    |
| 28  | [Response Wrapper](#28-phụ-lục-response-wrapper--bộ-quy-tắc-chung)         | Quy ước toàn dự án           |

---

## 1. Saga Pattern — Choreography only

### Phán đoán cuối cùng

**Không có Saga orchestrator / state machine / compensation class ở đâu cả.** Thay vào đó, dự án dùng **choreographed event-driven saga** — một chuỗi bước phối hợp qua RabbitMQ, **không** có service nào đứng giữa điều phối.

### Bằng chứng — "Mua flashcard set" là một saga điển hình

```text
marketplace-service
   OrderService.createOrder(...)
       │ (cùng transaction)
       ▼
   Lưu OutboxEvent [wallet.debit.requested]
       │ (OutboxProcessor scheduled job)
       ▼
   wallet.commands (RabbitMQ topic)
       │
       ▼
wallet-service
   WalletEventListener.handleWalletDebitRequested
       │ ── thành công ──► publish wallet.debit.succeeded
       │ ── thất bại ──► publish wallet.debit.failed
       │
       ▼
marketplace-service
   WalletEventHandler (idempotency check qua Inbox)
       │ ── success ──► set Order.status = PAID
       │                publish content.purchased
       │ ── fail   ──► set Order.status = FAILED
       │
       ▼
wallet-service + profile-service + flashcard-service
   + quiz-service + notification-service
   (consume content.purchased → cập nhật local read-model, tạo thông báo,…)
```

### File tham chiếu

- `src/services/marketplace-service/src/main/java/com/seika/marketplace_service/service/OrderService.java:38-116` — tạo `Order` ở trạng thái `PENDING_PAYMENT`, đồng thời ghi outbox.
- `src/services/marketplace-service/src/main/java/com/seika/marketplace_service/processor/OutboxProcessor.java:26-53` — `@Scheduled` đọc outbox, publish lên RabbitMQ.
- `src/services/wallet-service/src/main/java/com/cardy/walletService/consumer/WalletEventListener.java:28-55` — debit wallet, publish kết quả.
- `src/services/marketplace-service/src/main/java/com/seika/marketplace_service/service/WalletEventHandler.java:38-118` — cập nhật `Order` theo kết quả + publish tiếp `content.purchased`.
- `src/services/marketplace-service/src/main/java/com/seika/marketplace_service/enums/OrderStatus.java` — `PENDING_PAYMENT → PAID → CANCELLED|FAILED`.

### Compensation / Rollback

Không có compensation "thuần túy" (không gọi ngược để undo). Thay vào đó là **state machine của Order**: nếu `wallet.debit.failed`, order chuyển sang `FAILED` rồi xử lý thủ công ở admin/UI. Việc "bỏ cuộc" được kiểm soát bởi `Inbox idempotency` (§3) — event được xử lý at-most-once-per-step, nên saga an toàn trước retry/gửi lại.

### Vì sao lại chọn choreography thay vì orchestration

1. Hạ tầng RabbitMQ đã có sẵn (còn dùng cho notification, identity, marketplace events), nên "rải" thêm 2 bước event không tốn thêm vận hành.
2. Các bước rất tuyến tính (A → B → C), không có branching/business rule phức tạp — orchestrator chỉ thêm overhead.
3. Mỗi service đều có consumer cho nhiều mục đích khác nhau (notification, profile stats), nên việc thêm handler `wallet.debit.*` không tốn thêm deployment.

> Nếu trong tương lai có thêm bước "refund khi user đổi ý trong 24h" hay "approval đa cấp", hãy cân nhắc chuyển sang **orchestration Saga** với Temporal/Camunda.

---

## 2. Transactional Outbox Pattern — DÙNG × 2 nơi

### Tổng quan

Đây là pattern mạnh nhất của dự án. Triển khai ở 2 service: `marketplace-service` và `reward-service`.

### Ý tưởng cốt lõi

> Tránh **dual-write problem** (ghi DB và gửi RabbitMQ không cùng transaction). Thay vào đó: cùng transaction với business row, ghi thêm 1 row `outbox`; một job định kỳ đọc outbox, publish sang broker, cập nhật trạng thái.

### Instance A — marketplace-service

**Entity:** `src/services/marketplace-service/src/main/java/com/seika/marketplace_service/entity/OutboxEvent.java:14-62`

```java
@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(name = "outbox", indexes = {
    @Index(name = "idx_outbox_status_created", columnList = "status, created_at")
})
public class OutboxEvent {
  @Id @GeneratedValue private UUID id;

  private String aggregateType;     // e.g. "Order"
  private String aggregateId;
  private String eventType;         // e.g. "wallet.debit.requested"
  private String routingKey;        // e.g. "wallet.debit.requested"

  @Column(columnDefinition = "jsonb") @JdbcTypeCode(SqlTypes.JSON)
  private String payload;

  @Enumerated(EnumType.STRING)
  private OutboxStatus status;      // PENDING / SENT / FAILED
  private int retryCount;
  private Instant publishedAt;
  private String lastError;
}
```

**Repository:** `OutboxEventRepository.findTop50ByStatusInOrderByCreatedAtAsc(...)` — lấy batch gửi đi.

**Producer (cùng transaction với createOrder):** `OrderService.java:97-114`

```java
@Transactional
public Order createOrder(...) {
    // …lưu Order entity…
    OutboxEvent outboxEvent = OutboxEvent.builder()
        .aggregateType("Order")
        .aggregateId(order.getId())
        .eventType(event.getEventType())
        .routingKey("wallet.debit.requested")
        .payload(toJson(event))
        .status(OutboxStatus.PENDING)
        .retryCount(0)
        .build();
    outboxEventRepository.save(outboxEvent);
    return order;
}
```

**Poller:** `OutboxProcessor.java:26-53`

```java
@Component @RequiredArgsConstructor @Slf4j
public class OutboxProcessor {
    @Scheduled(fixedDelayString = "${outbox.processor.delay-ms:3000}")
    public void publishOutboxEvents() {
        List<OutboxEvent> batch = outboxEventRepository
            .findTop50ByStatusInOrderByCreatedAtAsc(List.of(OutboxStatus.PENDING, OutboxStatus.FAILED));
        for (OutboxEvent ev : batch) {
            try {
                rabbitTemplate.convertAndSend("wallet.commands", ev.getRoutingKey(), ev.getPayload());
                ev.setStatus(OutboxStatus.SENT);
                ev.setPublishedAt(Instant.now());
            } catch (Exception e) {
                ev.setRetryCount(ev.getRetryCount() + 1);
                ev.setLastError(e.getMessage());
                ev.setStatus(OutboxStatus.FAILED);  // thử lại ở lần poll kế tiếp
            }
            outboxEventRepository.save(ev);
        }
    }
}
```

Status enum: `OutboxStatus.java` (`PENDING / SENT / FAILED`).

### Instance B — reward-service

- Entity: `src/services/reward-service/src/main/java/com/seika/reward_service/outbox/OutboxEvent.java` (table `reward_outbox`, payload lưu TEXT).
- Repository: `OutboxEventRepository.findAllByOrderByCreatedAtAsc()`.
- Producer: `RewardProcessor.java:130-153` — `grantReward(...)` chèn row cùng transaction.
- Poller: `OutboxScheduler.java:23-49`. **Lưu ý:** cài đặt này `delete(...)` row sau khi publish (không giữ lại như marketplace).

### Tại sao chọn pattern này (và **không** dùng publish trực tiếp trong service)

| Phương án                                                      | Vấn đề                                                                           |
| -------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| Publish trực tiếp trong `@Transactional`                       | DB commit có thể fail sau khi RabbitMQ đã gửi → mất message.                     |
| Publish **sau** khi commit (qua `@TransactionalEventListener`) | RabbitMQ có thể down/network blip → mất message.                                 |
| **Outbox** (chọn)                                              | DB transaction = nguồn sự thật duy nhất; job sẽ gửi lại cho đến khi broker nhận. |

---

## 3. Inbox Pattern — DÙNG

### Tổng quan

Đối xứng với Outbox ở phía **nhận**: khi consumer xử lý 1 event, ghi 1 row inbox (unique theo `messageId`). Nếu row đã ở trạng thái `PROCESSED` thì skip — đảm bảo at-most-once side-effect ngay cả khi broker gửi trùng.

### Triển khai ở marketplace-service

**Entity:** `src/services/marketplace-service/src/main/java/com/seika/marketplace_service/entity/InboxEvent.java:14-65`

```java
@Entity
@Table(name = "inbox", uniqueConstraints = @UniqueConstraint(columnNames = "message_id"))
public class InboxEvent {
  @Id @GeneratedValue UUID id;
  @Column(name = "message_id", nullable = false) String messageId;  // unique
  private String aggregateType;
  private String eventType;
  @Column(columnDefinition = "jsonb") String payload;
  @Enumerated(EnumType.STRING) InboxStatus status;   // RECEIVED / PROCESSED / FAILED
  private Instant processedAt;
  // …
}
```

**Consumer:** `WalletEventHandler.java:38-85`

```java
@RabbitListener(queues = "marketplace.wallet.events.q")
@Transactional
public void handleWalletDebitEvent(WalletDebitResultEvent event) {
    Optional<InboxEvent> existing = inboxEventRepository.findByMessageId(event.getEventId());
    if (existing.isPresent() && existing.get().getStatus() == InboxStatus.PROCESSED) {
        log.info("Skipped wallet.debit event because it was already processed. eventId={}", event.getEventId());
        return;                                   // ← idempotency bằng Inbox
    }
    // …thực hiện nghiệp vụ…
    inboxEventRepository.save(InboxEvent.builder()
        .messageId(event.getEventId())
        .status(InboxStatus.PROCESSED)
        .processedAt(Instant.now())
        .build());
}
```

### Vì sao quan trọng

`wallet.debit.*` là trigger cho **nhiều side-effect** (tạo UserInventory, cộng doanh thu, gửi thông báo,…). Nếu RabbitMQ gửi trùng → trừ tiền 2 lần, cộng inventory 2 lần, teacher nhận tiền gấp đôi. **Inbox row check** chặn đúng chỗ đó.

### Một biến thể "dedup" khác trong dự án

`notification-service` dùng `findByEventId(...)` trực tiếp trên `Notification` entity để chống trùng thông báo (`NotificationRepository.findByEventId` được dùng trong `NotificationService.createNotification(...)`). Cùng ý tưởng, dữ liệu neo trên entity thay vì bảng inbox riêng.

`reward-service` cũng có một kiểm tra idempotency tương tự: `RewardProcessor.processQuizCompleted` xem `LearningRewardLog` đã có `(user, quiz)` chưa trước khi cộng thưởng.

---

## 4. Layered Architecture — DÙNG

### Bằng chứng

Áp dụng cho **mọi** service. Package layout chuẩn (theo `documentation/CODING_STANDARDS.md`):

```text
com.<root>.<service>
├── controller/   ← @RestController nhận HTTP, validate, gọi service
├── service/      ← @Service @RequiredArgsConstructor @Slf4j, giao dịch
├── repository/   ← Spring Data JpaRepository / MongoRepository
├── entity/       (or domain/) — POJO/entity JPA hoặc @Document Mongo
├── dto/          — XxxRequest / XxxResponse
├── mapper/       — MapStruct @Mapper
├── config/       — @Configuration (Rabbit, OpenAPI, CORS, …)
├── security/     — JwtFilter / JwtTokenService
├── event/        — *Event class (payload gửi qua Rabbit)
├── client/       — @FeignClient interface
├── consumer/     — @Component @RabbitListener
├── exception/    — *Exception + GlobalExceptionHandler
├── shared/       — ApiResponse / PagedResponse / constants
└── processor/    — @Scheduled job (OutboxProcessor, …)
```

### Ví dụ điển hình

`com.cardy.walletService` (`wallet-service`):

- `controller/WalletController.java` — endpoint REST.
- `service/WalletService.java` — `@Transactional` ở method cập nhật số dư; `@Transactional(readOnly = true)` ở method chỉ đọc.
- `repository/WalletRepository.java` — `JpaRepository<Wallet, UUID>`.

`com.seika.marketplace_service` (`marketplace-service`):

- `controller/OrderController.java`
- `service/OrderService.java` + `service/WalletEventHandler.java`
- `repository/OrderRepository.java`, `OrderItemRepository.java`, `OutboxEventRepository.java`, `InboxEventRepository.java`

### Quy tắc transaction

- Method ghi: `@Transactional` (mặc định `readOnly = false`).
- Method đọc: `@Transactional(readOnly = true)`.
- Outbox/Inbox producer/consumer cũng ở layer service, có `@Transactional` riêng.

### Tại sao chọn Layered thay vì Hexagonal

Hệ thống chưa có nhiều domain phức tạp, business logic không thay đổi adapter liên tục. Layered cho phép DI/controller xử lý HTTP, không phải truy tìm "port nào gọi adapter nào". Khi có thêm các hệ thống lưu trữ khác (search engine, key-value cache, gRPC sang bên thứ ba) thì mới xem xét Hexagonal.

---

## 5. Hexagonal / Ports & Adapters — KHÔNG dùng

### Phán đoán cuối cùng

Không có package `domain/port/in`, `domain/port/out`, `application/port`, `adapter/`. Repository là `JpaRepository`/`MongoRepository` concrete — không có interface riêng.

### Ví dụ "anti-port"

`OrderService.java:33-36` inject thẳng `OrderRepository`, `OutboxEventRepository`, etc.:

```java
@Service @RequiredArgsConstructor @Slf4j
public class OrderService {
    private final OrderRepository orderRepository;          // concrete, không qua interface
    private final OutboxEventRepository outboxEventRepository;
    private final OrderItemRepository orderItemRepository;
    // …
}
```

Nếu mai sau muốn mock, sử dụng `@MockBean` của Spring Boot Test chứ không có "port interface".

---

## 6. CQRS — KHÔNG dùng

### Phán đoán cuối cùng

Không tách read model / write model. Read và write đều đi qua cùng service, cùng repository.

### Một "ám chỉ" gần-CQRS

`flashcard-service` và `quiz-service` duy trì bảng "read mirror" `product_sales` được populate từ event consumer (`ContentPurchasedConsumer.java`). Đây **gần giống** một query-side denormalized view, nhưng:

- Vẫn nằm trong cùng schema với write-side khác của service — không tách DB.
- Read của dashboard teacher có thể vẫn join với collection gốc nếu cần dữ liệu sâu hơn.

Đánh giá: gọi là **"read-store materialized by event"** thì đúng hơn là CQRS proper.

### Vì sao chưa chính thức CQRS

Số lượng aggregate không lớn, query của dashboard không đòi hỏi consistency siêu nhanh (eventual consistency chấp nhận được). Khi một query nào đó trở thành bottleneck (ví dụ: "doanh thu của 10 nghìn teacher"), mới tách read model.

---

## 7. Event Sourcing — KHÔNG dùng

### Phán đoán cuối cùng

Không có Axon, không có event-stored aggregate, không có cơ chế replay. Dữ liệu lưu Postgres/Mongo là **current state**.

`OutboxEvent.payload` có vẻ giống event log nhưng:

- Thường bị `delete` hoặc chuyển trạng thái `SENT`/`FAILED` rồi dọn dẹp — không phải system-of-record.
- Chỉ phục vụ việc publish message an toàn.

`UserRegisteredEvent` và các event khác **chỉ** là integration envelope — chúng trao đổi giữa service, không phải lịch sử tổng quan của một aggregate.

---

## 8. API Gateway Pattern — DÙNG

### Trụ cột

`src/api-gateway/` là **entry point duy nhất**. Tất cả frontend request đều qua `http://localhost:8080/api/...`.

### Thành phần chính

| File                                   | Vai trò                                                           |
| -------------------------------------- | ----------------------------------------------------------------- |
| `ApiGatewayApplication.java`           | Boot main class.                                                  |
| `resources/application.yaml`           | Khai báo routes: `uri: lb://FLASHCARD-SERVICE` v.v., dùng Eureka. |
| `filter/AuthenticationFilter.java`     | JWT auth filter (xem dưới).                                       |
| `filter/GlobalLoggingFilter.java`      | Ghi log mọi request, đo thời gian (`order = -2`).                 |
| `config/OpenApiRouteConfig.java`       | Rewrite `/v3/api-docs/<service>` sang mỗi service.                |
| `config/OpenAPIAggregationConfig.java` | Tổng hợp Swagger UI.                                              |
| `config/CorsConfig.java`               | CORS.                                                             |

### AuthenticationFilter — chi tiết

`AuthenticationFilter.java:24-77`

```java
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (isPublicPath(exchange.getRequest().getPath())) return chain.filter(exchange);

        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }
        try {
            Claims claims = Jwts.parser()…parseSignedClaims(token).getPayload();
            // copy claims vào header downstream:
            exchange = exchange.mutate()
                .request(r -> r.header("X-User-Id", claims.get("userId", String.class))
                              .header("X-User-Name", claims.get("sub"))
                              .header("X-User-Roles", String.join(",", roles)))
                .build();
            return chain.filter(exchange);
        } catch (JwtException e) {
            return unauthorized(exchange);
        }
    }

    private boolean isPublicPath(PathContainer path) {
        AntPathMatcher m = new AntPathMatcher();
        // đọc app.api.public-endpoints từ Config Server
        // + các đường dẫn swagger
    }
}
```

Điểm tinh tế: **không** cần gọi identity-service mỗi lần — gateway tự verify JWT bằng `JWT_SECRET` chia sẻ từ `.env`. Trước khi xuống service, gateway đã gắn `X-User-Id/Name/Roles`, các service downstream chỉ cần đọc header (qua `JwtTokenService` riêng).

### Vì sao tách gateway

- **Một điểm áp dụng chính sách**: JWT, CORS, logging, rate-limit (nếu sau này thêm), Swagger aggregation — đều đặt tại 1 nơi.
- **Downstream không phụ thuộc JWT secret**, không phải implement OpenAPI discovery riêng.
- **Single entry point** → dễ đặt WAF/CDN, dễ xoay TLS, dễ blue-green.

---

## 9. Service Registry / Discovery — DÙNG

### Bằng chứng

- Server: `src/eureka/`.
- Mọi service POM có dependency `spring-cloud-starter-netflix-eureka-client` (verified trên wallet-service và nhiều service khác).
- Mọi service YAML được Config Server serve với:
  ```yaml
  eureka:
    client:
      enabled: true
      service-url:
        defaultZone: ${EUREKA_SERVER_URL}
  ```
- Gateway + Feign client dùng `lb://SERVICE-ID` để resolve:
  ```java
  @FeignClient(name = "wallet-service")
  public interface WalletClient { … }
  ```

### Vì sao Eureka thay vì Kubernetes-native DNS

Đề án đi theo stack Spring Cloud chuẩn; giữ Eureka giúp gateway dùng `lb://` chứ không phải đổi sang Kubernetes Services (cũng hoạt động nhưng kém đồng nhất với các pattern còn lại).

---

## 10. Config Server Pattern — DÙNG

### Bằng chứng

- Server: `src/config-service/` (port 8888).
- Per-service YAML: `src/config-service/src/main/resources/configs/{service}.yaml` và `{service}-dev.yaml` (và `-prod.yaml`).
- Mỗi microservice import `spring-cloud-starter-config`, kèm `fail-fast: true` + retry.
- `.env` ở repo root chứa JWT secret, DB URL, ports… → Config Server đọc và interpolate vào YAML.

### Ví dụ hot-knob

- `app.api.public-endpoints` — `AuthenticationFilter` lấy từ config, có thể đổi public path mà không cần rebuild gateway.
- `outbox.processor.delay-ms` — tune poll rate của `OutboxProcessor`.
- `spring.datasource.url` (và `…schema`) — chuyển từ môi trường này sang môi trường khác chỉ bằng profile.

### Lợi ích rõ ràng

- **Không rebuild** khi chuyển profile/dev/prod.
- **Không commit secret**: `.env` không push lên Git; Config Server chỉ interpolate từ runtime env.
- **Có thể khóa public endpoint** bằng cấu hình (không phải code).

---

## 11. Database per Service — DÙNG

### Bằng chứng

| Service              | DB       | Port        | Schema                       |
| -------------------- | -------- | ----------- | ---------------------------- |
| identity-service     | Postgres | 5432        | `identity-service-seika`     |
| profile-service      | Postgres | 5433        | `profile-service-seika`      |
| wallet-service       | Postgres | 5434        | `wallet-service-seika`       |
| marketplace-service  | Postgres | 5435        | `marketplace-service-seika`  |
| notification-service | MongoDB  | 27017 (rs0) | `notification-service-seika` |
| flashcard-service    | MongoDB  | 27017 (rs0) | `flashcard-service-seika`    |
| quiz-service         | MongoDB  | 27017 (rs0) | `quiz-service-seika`         |
| reward-service       | Postgres | (5436)      | `reward-service-seika`       |

### Vì sao

- Tránh **shared schema anti-pattern** (DB coupling giữa các service).
- Cho phép mỗi service tự chọn loại DB phù hợp (Postgres quan hệ, Mongo tài liệu).
- Backup/restore/scaling độc lập.

`CLAUDE.md` và `documentation/DOCKER_DATABASES_VISUALIZATION.md` có sơ đồ trực quan.

---

## 12. Circuit Breaker — KHÔNG dùng

### Phán đoán cuối cùng

Không có Resilience4j, không có Spring Cloud Circuit Breaker. Feign client không có `fallback=…`.

`CardSetService` khi gọi `WalletClient` chỉ try/catch `FeignException` rồi ném lại `RuntimeException` — đây là **fail-fast thủ công**, không phải circuit breaker.

### Lý do chưa cần

Dự án nhỏ, traffic chưa đủ để quan sát cascade failure. Khi thấy một Feign call phải retry hoặc timeout thường xuyên, hãy thêm Resilience4j + fallback (trả `degraded response` thay vì 500).

---

## 13. Bulkhead Pattern — KHÔNG dùng

### Phán đoán cuối cùng

Không có isolation thread pool / semaphore cho Feign client, không có Resilience4j BulkheadConfig.

---

## 14. MapStruct — DÙNG

### Bằng chứng

Khai báo trong parent POM:

```xml
<dependency>
  <groupId>org.mapstruct</groupId>
  <artifactId>mapstruct</artifactId>
  <version>${mapstruct.version}</version>  <!-- 1.6.3 -->
</dependency>
```

Sử dụng trong ít nhất 4 service:

- `identity-service`: `AuthMapper.java:14-40`

  ```java
  @Mapper(componentModel = "spring")
  public interface AuthMapper {
      @Mapping(target = "password", source = "encodedPassword")
      @Mapping(target = "roles", source = "roles")
      User toUser(RegisterRequest req, String encodedPassword, Set<Role> roles);

      AuthResponse toAuthResponse(...);
      UserInfoResponse toUserInfoResponse(User user);
  }
  ```

- `flashcard-service`: `CardSetMapper.java` dùng `expression = "java(cardSet.getCards() != null ? cardSet.getCards().size() : 0)"` để tính `totalCards`.
- `profile-service`: `UserProfileMapper.java`.
- `notification-service`: `NotificationMapper.java`.

### Vì sao

- Loại bỏ DTO ↔ entity boilerplate.
- `componentModel = "spring"` → DI như bean thường.
- Compile-time generation → không overhead runtime.
- `@Mapping(target, source = ...)` cover được các phép tính nhỏ (vd. đếm card).

---

## 15. Repository Pattern — DÙNG

### Bằng chứng

Spring Data JPA / Mongo trên mọi service.

**JPA:**

- `WalletRepository extends JpaRepository<Wallet, UUID>`.
- `TransactionRepository extends JpaRepository<Transaction, UUID>`.
- `SystemConfigRepository extends JpaRepository<SystemConfig, UUID>` (wallet-service).
- `OrderRepository extends JpaRepository<Order, UUID>` (marketplace-service).

**Mongo:**

- `NotificationRepository extends MongoRepository<Notification, UUID>`.
- `CardSetRepository extends MongoRepository<CardSet, UUID>` (flashcard-service).

Custom finder phổ biến:

- Derived queries: `findByUserId`, `findByMessageId`, `existsByUserIdAndProductId`.
- JPQL aggregations: `findDailyRevenueBySeller`, `findTopProductsBySeller` (trong `OrderItemRepository.java`).

---

## 16. Builder Pattern — DÙNG (Lombok)

### Bằng chứng

`@Builder` xuất hiện ở hầu hết entity, DTO, event:

- `Order.java`, `OutboxEvent.java`, `InboxEvent.java` (marketplace).
- `Wallet.java`, `Transaction.java`, `SystemConfig.java` (wallet).
- `UserRegisteredEvent.java` (identity).

Ví dụ chuỗi:

```java
Wallet wallet = Wallet.builder()
    .userId(request.getUserId())
    .balance(request.getInitialBalance())
    .build();
```

### Vì sao

Lombok sinh code tại compile-time; không tốn performance. Tránh constructor "nhiều tham số". Kết hợp MapStruct cho phép: builder cho entity DTO, MapStruct sinh từ entity.

---

## 17. Strategy Pattern — KHÔNG dùng cổ điển

### Một "ám chỉ" sealed-interface-with-pattern-matching

`quiz-service/.../domain/BaseQuiz.java:14-39` và `service/QuizService.java:91-115`:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public sealed interface BaseQuiz permits MatchingQuiz, ReorderQuiz, FillInBlankQuiz, McqQuiz { … }

BaseQuiz quiz = switch (request) {
    case MatchingRequest       req -> createMatching(req);
    case ReorderRequest        req -> createReorder(req);
    case FillInBlankRequest    req -> createFillInBlank(req);
    case McqRequest            req -> createMcq(req);
    default -> throw new IllegalArgumentException("Invalid Quiz Type");
};
```

Đây là **Java 21 sealed interface + pattern matching switch** — về bản chất là Strategy pattern, nhưng implementation **không** qua `Map<String, Strategy>` hay interface `PaymentStrategy`/`Context`. Pattern matching đã thay thế phần boilerplate.

---

## 18. Observer / Pub-Sub — DÙNG (RabbitMQ)

### Topology

5 topic exchange + 1 fanout:

| Exchange             | Loại   | Routing key chính                                           | Consumers                                                                              |
| -------------------- | ------ | ----------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| `identity.events`    | topic  | `user.registered`                                           | wallet-service, profile-service, notification-service                                  |
| `learning.events`    | topic  | `reward.granted`, `deck.completed`, `quiz.completed`        | wallet-service (reward), reward-service                                                |
| `marketplace.events` | topic  | `content.purchased`, `content.created`, `content.reviewed`  | profile-service, flashcard-service, quiz-service, wallet-service, notification-service |
| `content.events`     | topic  | `flashcard.set.created/updated`, `quiz.set.created/updated` | (search, recommend)                                                                    |
| `wallet.commands`    | topic  | `wallet.debit.requested`                                    | wallet-service                                                                         |
| `wallet.events`      | topic  | `wallet.debit.succeeded/failed`, `wallet.updated.*`         | marketplace-service, notification-service                                              |
| `learn.exchange`     | fanout | (none)                                                      | broadcast `LearnProgressDTO`                                                           |

Khai báo exchange/queue trong từng `RabbitMQConfig.java` của mỗi service. Tổng cộng **20 `@RabbitListener`** annotations.

### Tại sao

- Một event có thể trigger nhiều effect ở nhiều service (vd. `content.purchased` → wallet cộng tiền + profile stats + flashcard mirror + quiz mirror + notification).
- Service không cần biết ai đang lắng nghe.
- Scale: thêm consumer mới không cần sửa producer.

---

## 19. Factory Pattern — KHÔNG dùng

Không có `*Factory.java` userland. Object construction dựa vào `new` + Lombok builder + MapStruct.

---

## 20. Decorator Pattern — KHÔNG dùng (OO)

Project **không** dùng OO decorator (interface + concrete + wrapping). Tuy nhiên, các filter theo SPI là một dạng "wrapping" tương đương:

- `JwtAuthenticationFilter extends OncePerRequestFilter` (identity-service).
- `AuthenticationFilter implements GlobalFilter, Ordered` (gateway).
- `GlobalLoggingFilter implements GlobalFilter, Ordered` (gateway).

Đây là **Servlet Filter chain** và **WebFlux GlobalFilter chain** — đúng vai trò "decorator" nhưng theo SPI framework chứ không phải pattern OO kinh điển.

---

## 21. Dependency Injection — DÙNG

### Ba idiom đang dùng

1. **Lombok `@RequiredArgsConstructor`** — sinh constructor cho `final` fields:

   ```java
   @Service @RequiredArgsConstructor @Slf4j
   public class WalletService {
       private final WalletRepository walletRepository;
       private final TransactionRepository transactionRepository;
       private final ApplicationEventPublisher eventPublisher;
   }
   ```

2. **`@Autowired` on final field** (chỉ `WalletController`):

   ```java
   @RestController
   public class WalletController {
       @Autowired private final WalletService walletService;
       @Autowired private final SystemConfigService systemConfigService;
   }
   ```

3. **`@Value` cho config**: `RewardProcessor.java:32-57` inject nhiều `@Value("${reward...}")` cho mọi rule thưởng.

### Lợi ích

- `final` field + constructor injection → immutable, dễ unit test (không cần reflection).
- Bean scope mặc định là singleton; service không giữ state per-request.

---

## 22. Idempotency Patterns — DÙNG

### Ba flavor

1. **Inbox row** (đã trình bày ở §3) — `marketplace-service.WalletEventHandler`.
2. **Dedup trên entity chính** — `NotificationRepository.findByEventId(...)` chặn trùng `Notification`.
3. **Per-recipient log** — `RewardProcessor.processQuizCompleted` xem `LearningRewardLog` đã có `(user, quiz)` chưa → skip nếu có.

Cả ba đều neo vào **khoá unique** ở tầng DB → đảm bảo idempotency kể cả khi 2 request xử lý song song.

---

## 23. Dead Letter Queue — KHÔNG cấu hình

### Phán đoán cuối cùng

Không có `x-dead-letter-exchange` trong bất kỳ `Queue` bean nào. Không có `DeadLetterExchange` declaration.

### Cơ chế retry hiện tại

- `OutboxProcessor`: increment `retryCount`, set `FAILED`, sẽ được pick up ở poll kế tiếp — không có giới hạn số lần, mọi event "poison" sẽ ở `FAILED` mãi (cần operator dọn).
- `@RabbitListener` thường ném `Exception` → AMQP mặc định: **no requeue, no DLQ** (message biến mất).
- Một số nơi ném `AmqpRejectAndDontRequeueException` cho lỗi deserialize (cũng biến mất).

### Khuyến nghị

Khi tăng scale, hãy:

1. Thêm DLX cho mỗi queue để giữ lại poison message phục vụ audit.
2. Cấu hình retry với delay + max-retry-count ở mức broker (`x-message-ttl` + `x-dead-letter-exchange`).

---

## 24. Outbox + Choreography — KẾT HỢP

Đã trình bày chi tiết ở §1 và §2. Tóm tắt:

| Bước                        | Thành phần                                         | Vai trò                                |
| --------------------------- | -------------------------------------------------- | -------------------------------------- |
| Tạo Order + ghi Outbox      | marketplace-service (`OrderService`)               | Cùng transaction, không có dual-write. |
| Publish Outbox sang broker  | marketplace-service (`OutboxProcessor` @Scheduled) | Worker định kỳ.                        |
| Debit wallet                | wallet-service (consumer)                          | Side effect tài chính.                 |
| Cập nhật Order theo kết quả | marketplace-service (`WalletEventHandler` + Inbox) | State machine.                         |
| Phát tán content.purchased  | marketplace-service → nhiều consumer               | Update read-model các nơi.             |

---

## 25. Strangler Fig Pattern — KHÔNG dùng

Dự án là greenfield — không có monolith cũ để "chặt dần". Repository có 2 project loosely-coupled (Spring backend + React frontend) ngay từ đầu, không phải migration từ monolith.

---

## 26. Sidecar Pattern — KHÔNG dùng trong code ứng dụng

`docker-compose.observability.yml` chạy Prometheus / Grafana / Loki / Tempo / Promtail như các sidecar, nhưng đây là **hạ tầng triển khai**, không phải design pattern trong code Spring.

Trong code Spring: không có Envoy/Istio sidecar injection, không có gRPC co-processor, không có shared cache sidecar truy cập từ Java.

---

## 27. BFF (Backend for Frontend) — KHÔNG dùng

Một gateway duy nhất phục vụ cả dashboard student, teacher và admin (route trong `routes.tsx` frontend). Không có controller riêng cho mobile, admin, teacher. Nếu sau này có mobile app riêng, mới cân nhắc tách BFF.

---

## 28. Phụ lục: Response Wrapper — quy ước chung

`CLAUDE.md` có mô tả, `src/services/quiz-service/RESPONSE_WRAPPER_USAGE.md` có ví dụ. Tóm tắt:

```java
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private Instant timestamp;
    // factory + builder
}

public class PagedResponse<T> {
    private List<T> content;
    private int page, size;
    private long totalElements, totalPages;
    // static of(Page<T> p)
}
```

Mọi controller trả `ResponseEntity<ApiResponse<T>>`; mọi method paginated trả `ApiResponse<PagedResponse<X>>`. Mọi exception được `@RestControllerAdvice` dịch sang `ApiResponse` (giống nhau ở mọi service).

> Pattern này **không** phải Gang-of-Four kinh điển, nhưng là **convention quan trọng nhất** của dự án — gần như mọi thứ liên quan đến HTTP đều đi qua nó.

---

## Tổng hợp: Pattern mạnh nhất & điểm thiếu

### Mạnh nhất

| Pattern                       | Mức độ  | Đặc điểm                                                                     |
| ----------------------------- | ------- | ---------------------------------------------------------------------------- |
| **Outbox + Inbox**            | Rất tốt | Triển khai ở 2 service, giải quyết dual-write và duplicate delivery.         |
| **API Gateway + JWT filter**  | Rất tốt | Một entry point, validate token local, inject user headers xuống downstream. |
| **Pub/Sub (RabbitMQ)**        | Rất tốt | 6 exchange, 20 listener; là "xương sống" giao tiếp service.                  |
| **MapStruct**                 | Tốt     | Khử boilerplate DTO ↔ entity.                                                |
| **Layered + Repository + DI** | Chuẩn   | Đúng pattern Spring "kinh điển".                                             |

### Còn thiếu/kém (gợi ý cải tiến)

| Pattern                                  | Vì sao nên thêm                                                                   |
| ---------------------------------------- | --------------------------------------------------------------------------------- |
| **Saga Orchestrator** (Temporal/Camunda) | Khi có workflow phân nhánh (refund, approval nhiều cấp) thay vì step đường thẳng. |
| **Circuit Breaker** (Resilience4j)       | Khi Feign giữa service xuất hiện flakiness, fail-fast tay không đủ.               |
| **DLQ** cho mỗi RabbitMQ queue           | Audit khi poison message. Hiện đang bị "biến mất".                                |
| **CQRS** đọc riêng (read-store)          | Khi dashboard teacher phức tạp và query nặng.                                     |
| **BFF**                                  | Khi mobile/web có yêu cầu render khác nhau (mobile cần payload gọn hơn).          |
| **Hexagonal**                            | Khi muốn swap DB/search-engine/cache mà không sửa service.                        |
| **Event Sourcing**                       | Khi cần audit/replay cho finance (chưa cần cho Seika hiện tại).                   |

> Hai **lỗ hổng** đáng chú ý nhất cho vận hành thực tế:
>
> 1. **Thiếu circuit breaker / DLQ** → khi 1 service down, request khác có thể dồn lại → degrade dây chuyền.
> 2. **Saga không có explicit timeout / compensation** → nếu một bước "treo", Order kẹt ở `PENDING_PAYMENT` vĩnh viễn.
>
> Khi triển khai production, hãy cân nhắc bổ sung các pattern tương ứng.

---

> Tài liệu này phản ánh nhánh `master` tại thời điểm viết. Khi thêm service mới hoặc thay đổi kiến trúc, hãy cập nhật tương ứng — đặc biệt các mục §1 (Saga), §2 (Outbox), §8 (Gateway), §18 (Pub/Sub) là những chỗ dễ trôi theo thời gian.
