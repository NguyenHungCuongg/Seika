# Báo cáo Đánh giá Kết quả Load Testing & Chaos Engineering

**Ngày thực hiện:** 24/07/2026  
**Công cụ:** Grafana k6, Docker  
**Mục tiêu:** Kiểm chứng khả năng chịu tải, Rate Limiting và tính năng Circuit Breaker của hệ thống Seika.

---

## 1. Tóm tắt kết quả (Executive Summary)

Hệ thống thể hiện **hiệu năng xuất sắc** và **khả năng sinh tồn (resilience) tuyệt vời** dưới điều kiện khắc nghiệt. Cơ chế Circuit Breaker hoạt động chính xác 100%, bảo vệ hệ thống khỏi Cascading Failure.

Tuy nhiên, bài test Rate Limiting chưa đạt kỳ vọng do có sự sai lệch giữa cấu hình K6 và cấu hình thực tế của API Gateway.

---

## 2. Phân tích chi tiết

### 2.1. Đánh giá Circuit Breaker (Thành công mỹ mãn)

Trong Phase 3 của bài test, `seika-wallet-service` đã bị chủ động đóng băng (pause).

- **Kết quả:** Toàn bộ các check liên quan đến Circuit Breaker đạt tỷ lệ Pass 100%:
  - `✓ Circuit Breaker: API is responsive (no timeout)`
  - `✓ Circuit Breaker: No Gateway Error (502/503/504)`
- **Nhận xét:** Hệ thống không hề bị treo. Thay vì bắt người dùng chờ 60 giây timeout, Circuit Breaker đã lập tức ngắt mạch và kích hoạt **Fallback**, giúp thời gian phản hồi (http_req_duration) duy trì ở mức tối đa chỉ **42.63ms**. Gateway hoàn toàn không gặp lỗi 502/504. Đây là một kết quả hoàn hảo cho Chaos Engineering.

### 2.2. Đánh giá Độ trễ và Hiệu năng (Hiệu năng cực cao)

Hệ thống xử lý lượng request lớn với tốc độ đáng kinh ngạc:

- `login_duration`: Trung bình **5.51ms**, 95% số request (p95) hoàn thành dưới **7.99ms**.
- `flashcard_duration`: Trung bình **3.04ms**, 95% số request (p95) hoàn thành dưới **4.53ms**.
- `http_req_duration` (Toàn bộ API): 95% số request hoàn thành dưới **7.56ms**.
- **Nhận xét:** Thời gian phản hồi tính bằng một chữ số mili-giây cho thấy các Backend services và API Gateway xử lý logic nội bộ vô cùng tối ưu, không có dấu hiệu bị "thắt cổ chai" (bottleneck) ở CPU hay Database.

_(Lưu ý: Chỉ số `http_req_failed: 85.26%` hiển thị trên màn hình là do kịch bản test cố tình gửi sai mật khẩu. K6 mặc định đếm các mã lỗi 4xx như 401 Unauthorized là failed. Tuy nhiên, custom metric `errors` của chúng ta ghi nhận `0.00%` lỗi, nghĩa là hệ thống phản hồi đúng như thiết kế)._

### 2.3. Đánh giá Rate Limiting (Cần điều chỉnh)

Chỉ số `rate_limited` trả về `0.00%`, dẫn đến thông báo lỗi màu đỏ ở cuối kịch bản: `thresholds on metrics 'rate_limited' have been crossed`.

- **Nguyên nhân:** Có sự bất đồng bộ giữa cấu hình Gateway và Kịch bản Test.
  - Trong `api-gateway.yaml`, chúng ta đang cấu hình cho phép **50 requests/giây** (`replenishRate: 50`) và tối đa **100 requests burst** (`burstCapacity: 100`).
  - Trong kịch bản k6 `load-test.js`, tốc độ bắn phá (Brute-force) chỉ được thiết lập là **30 requests/giây** (`rate: 30`).
- **Hệ quả:** Vì tốc độ tấn công (30) nhỏ hơn sức chịu đựng của hệ thống (50), nên Redis Rate Limiter chưa bị kích hoạt. Không có request nào bị trả về mã 429 (Too Many Requests).

---

## 3. Khuyến nghị và Hành động tiếp theo

1. **Điều chỉnh cấu hình Rate Limiting để test lại:**
   - **Cách 1:** Mở file `scripts/load-test.js` và tăng `rate` trong scenario `rate_limit_test` từ `30` lên `80` hoặc `150`.
   - **Cách 2:** Mở file `api-gateway.yaml`, hạ giới hạn `replenishRate` xuống `10` và `burstCapacity` xuống `20`.
   - _Nên thực hiện cách 2 để bảo vệ hệ thống chặt chẽ hơn._

2. **Giữ nguyên cấu hình Circuit Breaker và Fallback:** Cơ chế hiện tại đang hoạt động cực kỳ hoàn hảo, không cần thay đổi.

3. **Báo cáo đồ án:** Kết quả này (sau khi fix Rate Limit) rất tuyệt vời để mang đi bảo vệ đồ án/demo cho giáo viên. Thời gian phản hồi 7ms dưới môi trường mô phỏng đứt gãy service là một con số rất ấn tượng.
