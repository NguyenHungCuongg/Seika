# Hướng Dẫn Sử Dụng Observability Stack Từng Bước

Tài liệu này hướng dẫn bạn cách truy cập và sử dụng bộ công cụ Observability (Grafana, Prometheus, Loki, Tempo) đã được tích hợp vào project Seika.

## Bước 1: Khởi động hệ thống

Bật terminal tại thư mục gốc của project (nơi chứa file `docker-compose.yml`) và chạy lệnh sau để khởi động cả hệ thống microservices và bộ công cụ theo dõi:

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d --build
```

_Đợi khoảng 1-2 phút để tất cả các service (đặc biệt là Eureka, Config server và các microservice) khởi động hoàn toàn._

---

## Bước 2: Truy cập Giao Diện Trung Tâm (Grafana)

Tất cả thao tác theo dõi (Metrics, Logs, Traces) đều được thực hiện trên một màn hình duy nhất là Grafana. Bạn không cần phải mở giao diện của Prometheus, Loki hay Tempo.

1. Mở trình duyệt và truy cập: **[http://localhost:3000](http://localhost:3000)**
2. Hệ thống đã được cấu hình tự động đăng nhập (Username và Password đều là admin).

---

## Bước 3: Xem Metrics (Chỉ số hoạt động)

Chúng ta sẽ xem biểu đồ CPU, RAM, và số lượng Request của các service.

1. Ở menu bên trái của Grafana, click vào icon **Dashboards** (hình 4 ô vuông).
2. Chọn thư mục **General** (hoặc danh sách hiển thị bên dưới).
3. Click vào Dashboard có tên là: **Spring Boot 3.x System Monitor**.
4. **Cách sử dụng màn hình này:**
   - Ở góc trên cùng bên trái, bạn sẽ thấy các ô Dropdown (Ví dụ: `application`, `instance`).
   - Click vào `application` và chọn một service bất kỳ, ví dụ: `wallet-service`.
   - Các biểu đồ bên dưới sẽ tự động thay đổi, hiển thị:
     - **Uptime:** Thời gian service đã chạy.
     - **CPU Usage:** Mức độ ngốn CPU.
     - **Memory:** Lượng RAM đang chiếm dụng.
     - **HTTP Requests:** Biểu đồ hiển thị có bao nhiêu request thành công (200) hoặc thất bại (400/500).

---

## Bước 4: Xem Logs tập trung (Loki)

Thay vì gõ `docker logs seika-wallet-service-1`, bạn có thể xem log của tất cả service tại một nơi.

1. Ở menu bên trái của Grafana, click vào biểu tượng la bàn có tên là **Explore**.
2. Nhìn lên góc trên cùng bên trái của màn hình Explore, có một Dropdown chọn Datasource. Trỏ vào đó và chọn **Loki**.
3. **Cách xem log của một service:**
   - Click vào nút **Label filters** (hoặc khung Query).
   - Dòng 1 chọn: `container`
   - Dòng 2 chọn: `seika-wallet-service-1` (hoặc tên bất kỳ service nào bạn muốn xem).
   - Click nút **Run query** (màu xanh dương ở góc phải).
   - Phía dưới màn hình sẽ hiển thị toàn bộ log của service đó, giống y hệt như trên terminal!
4. **Cách tìm kiếm lỗi:**
   - Thêm vào khung query ký hiệu tìm kiếm: `{container="seika-wallet-service-1"} |= "ERROR"` (Điều này sẽ lọc ra tất cả các dòng log có chữ ERROR).

---

## Bước 5: Xem Traces (Dấu vết của một Request)

Traces giúp bạn xem Request đó chạy từ Gateway -> Service A -> Service B mất bao nhiêu mili-giây.

1. Vẫn trong màn hình **Explore**, chuyển Datasource ở góc trên cùng bên trái từ Loki sang **Tempo**.
2. Click vào tab **Search**.
3. Trong ô `Service Name`, chọn ví dụ: `flashcard-service` hoặc `api-gateway`.
4. Bấm **Run query**.
5. Một danh sách các Trace ID sẽ hiện ra phía dưới kèm theo thời gian xử lý (Ví dụ: 150ms, 20ms).
6. **Click vào một Trace ID bất kỳ:**
   - Một sơ đồ kiểu Gantt Chart sẽ hiện ra.
   - Bạn sẽ thấy rõ một đường ngang biểu diễn request vào `api-gateway`. Bên dưới nó thụt lề vào trong là `identity-service` (xác thực token), rồi đến `flashcard-service`.
   - Thanh nào càng dài nghĩa là hàm đó/service đó chạy càng lâu!

---

## Bước 6: Kỹ năng Nâng Cao - Trace-to-Log và Log-to-Trace

Đây là tính năng "đáng đồng tiền bát gạo" nhất!

### Kịch bản 1: Từ Log lỗi -> Xem toàn bộ hành trình

1. Vào **Explore -> chọn Loki**. Tìm kiếm log có lỗi.
2. Bạn sẽ thấy dòng log trông như thế này:
   `INFO [wallet-service, 65b8c123a4b6c, 87d5b91a] - Insufficient balance...`
3. Chú ý cái mã `65b8c123a4b6c`. Đó chính là **Trace ID**. Trong Grafana, đoạn mã này sẽ được bôi màu xanh và **có thể click được**.
4. Click thẳng vào đoạn mã đó. Grafana sẽ tự động bật màn hình Tempo và vẽ ra biểu đồ chạy của đúng request bị lỗi đó! Bạn sẽ biết ai đã gọi cái API này dẫn đến lỗi.

### Kịch bản 2: Từ biểu đồ Trace chậm -> Xem log của đoạn code đó

1. Vào **Explore -> chọn Tempo**. Bạn tìm thấy một thanh màu cam chạy quá chậm (vd: mất tận 3 giây để gọi DB).
2. Bên cạnh thanh biểu đồ đó có một nút nhỏ ghi chữ **Logs**.
3. Bấm vào nút đó, Grafana sẽ tự động chuyển sang Loki và show cho bạn các dòng Log được in ra _đúng trong cái khoảng thời gian 3 giây đó_ của service đó! Bạn sẽ đọc được nguyên nhân gây chậm (vd: Hibernate in ra câu query SQL quá phức tạp).
