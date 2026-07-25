# Báo cáo Kết quả Load Testing & Chaos Engineering

**Ngày thực hiện:** 25/07/2026  
**Công cụ:** Grafana k6 + Docker (`chaos-test.ps1`)  
**Mục tiêu:** Kiểm chứng ba cơ chế bảo vệ hệ thống Seika: **Rate Limiting**, **Circuit Breaker**, và **hiệu năng tổng thể** dưới tải cao kết hợp chủ động gây lỗi (Chaos Engineering).

---

## 1. Tóm tắt kết quả

| Tiêu chí                                        | Kết quả                                                   | Trạng thái |
| ----------------------------------------------- | --------------------------------------------------------- | ---------- |
| Circuit Breaker (Resilience4j + Feign Fallback) | 100% request vẫn phản hồi khi Wallet Service bị đóng băng | ✅ ĐẠT     |
| Rate Limiting (Redis Token Bucket)              | 78.81% request auth bị chặn (1 183/1 501) với HTTP 429    | ✅ ĐẠT     |
| Hiệu năng tổng thể                              | p95 = 21.14ms, p99 = 68.43ms, error rate = 0.00%          | ✅ ĐẠT     |
| Thresholds K6                                   | Toàn bộ 3/3 threshold PASSED                              | ✅ ĐẠT     |

Toàn bộ **6 457 checks** đều thành công (100.00%), không có check nào thất bại.

---

## 2. Phân tích chi tiết

### 2.1. Circuit Breaker — Ngăn chặn Cascading Failure

**Kịch bản Chaos:** Script `chaos-test.ps1` thực hiện 5 phase tuần tự:

1. **Phase 1:** Khởi động K6 load test ở background.
2. **Phase 2:** Warm-up 15 giây để hệ thống ổn định.
3. **Phase 3 — CHAOS INJECTION:** Chạy `docker pause seika-wallet-service-1` để đóng băng hoàn toàn Wallet Service trong 30 giây.
4. **Phase 4 — RECOVERY:** Unpause Wallet Service, chờ 20 giây để Circuit Breaker chuyển HALF-OPEN → CLOSED.
5. **Phase 5:** Thu thập kết quả K6.

**Kết quả:**

| Check                                             | Kết quả      |
| ------------------------------------------------- | ------------ |
| `Circuit Breaker: API is responsive (no timeout)` | ✅ 100% PASS |
| `Circuit Breaker: No Gateway Error (502/503/504)` | ✅ 100% PASS |
| `Health: Gateway is responsive`                   | ✅ 100% PASS |
| `Health: Response time < 2000ms`                  | ✅ 100% PASS |

**Nhận xét:** Khi Wallet Service bị đóng băng, các service phụ thuộc (Identity, Flashcard) không hề bị treo hay timeout 60 giây như trước khi có Circuit Breaker. Thay vào đó, Resilience4j phát hiện lỗi liên tiếp, mở circuit (OPEN state), và kích hoạt **Fallback** — trả về giá trị mặc định ngay lập tức (0ms). Toàn bộ Gateway không gặp bất kỳ lỗi 502/503/504 nào, chứng tỏ hệ thống **hoàn toàn miễn nhiễm** với sự cố ở downstream service.

---

### 2.2. Rate Limiting — Chặn Brute-force & API Abuse

**Kịch bản test:** Scenario `rate_limit_test` bắn 50 requests/giây liên tục trong 30 giây vào endpoint `/api/auth/login` (cấu hình giới hạn: 10 token/giây, burst tối đa 20 token).

**Kết quả:**

| Metric                 | Giá trị                                     |
| ---------------------- | ------------------------------------------- |
| `rate_limited`         | **78.81%** (1 183 / 1 501 requests bị chặn) |
| `rate_limit_429_total` | **1 183 requests** trả về HTTP 429          |
| Threshold `rate>0`     | ✅ PASSED                                   |

**Nhận xét:** Với tốc độ 50 req/s, giỏ token (capacity 20, replenish 10/s) cung cấp tối đa ~30 token trong giây đầu tiên, sau đó chỉ còn 10 token/giây. Kết quả **78.81% bị chặn** hoàn toàn khớp với lý thuyết Token Bucket: chỉ ~21% request đi qua (≈10 token/s ÷ 50 req/s), phần còn lại bị Gateway từ chối với HTTP 429 Too Many Requests. Rate Limiter hoạt động chính xác như thiết kế.

---

### 2.3. Hiệu năng tổng thể

**Tải:** 3 scenarios chạy song song — 50 VUs normal load, 50 VUs rate limit test, 5 VUs circuit breaker probe — tổng cộng **3 979 HTTP requests** trong ~1m40s.

| Metric               | avg     | med     | p90     | p95     | p99     | max      |
| -------------------- | ------- | ------- | ------- | ------- | ------- | -------- |
| `http_req_duration`  | 9.91ms  | 6.46ms  | 15.82ms | 21.14ms | 68.43ms | 307.39ms |
| `flashcard_duration` | 4.57ms  | 4.04ms  | 6.19ms  | 7.92ms  | —       | 39.75ms  |
| `login_duration`     | 12.30ms | 10.44ms | 17.44ms | 20.94ms | —       | 195.11ms |

**Thresholds:**

| Threshold               | Điều kiện  | Giá trị thực tế | Kết quả |
| ----------------------- | ---------- | --------------- | ------- |
| `errors`                | rate < 10% | **0.00%**       | ✅ PASS |
| `http_req_duration p95` | < 1000ms   | **21.14ms**     | ✅ PASS |
| `http_req_duration p99` | < 2000ms   | **68.43ms**     | ✅ PASS |

**Nhận xét:** Thời gian phản hồi p95 chỉ **21ms** — nhanh gấp **47 lần** so với ngưỡng cho phép (1000ms). Custom metric `errors` ghi nhận **0.00%** lỗi, nghĩa là hệ thống xử lý đúng 100% logic nghiệp vụ. Chỉ số `http_req_failed: 87.55%` hiển thị cao là do K6 mặc định đếm HTTP 4xx (401 Unauthorized từ đăng nhập sai, 429 từ rate limit) là "failed" — đây là hành vi đúng và mong đợi của kịch bản test, không phải lỗi hệ thống.

---

### 2.4. Checks tổng hợp

Tất cả 9 checks trong kịch bản K6 đều đạt 100%:

| Check                                             | Kết quả |
| ------------------------------------------------- | ------- |
| ✓ Health: Gateway is responsive                   | 100%    |
| ✓ Health: Response time < 2000ms                  | 100%    |
| ✓ Circuit Breaker: API is responsive (no timeout) | 100%    |
| ✓ Circuit Breaker: No Gateway Error (502/503/504) | 100%    |
| ✓ Login: Response time < 1000ms                   | 100%    |
| ✓ Login: Valid HTTP status (non-5xx)              | 100%    |
| ✓ Flashcard: Response time < 500ms                | 100%    |
| ✓ Flashcard: No Gateway Error (502/503/504)       | 100%    |
| ✓ Rate Limit: Received 200/400/401 or 429         | 100%    |

---

## 3. Kết luận

Ba cơ chế bảo vệ đã được kiểm chứng thành công trong điều kiện vừa chịu tải cao vừa bị chủ động gây lỗi:

1. **Circuit Breaker + Fallback:** Ngắt mạch thành công khi Wallet Service sập, hệ thống tiếp tục phục vụ bình thường, tự phục hồi khi service trở lại.
2. **Rate Limiting (Token Bucket + Redis):** Chặn chính xác 78.81% request vượt ngưỡng tại API Gateway, bảo vệ backend khỏi brute-force và API abuse.
3. **Hiệu năng:** Thời gian phản hồi p95 dưới 22ms dưới tải ~39 req/s với 95 VUs đồng thời, không có lỗi hệ thống nào phát sinh.
