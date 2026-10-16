# Hệ Thống Nhà Thuốc Trực Tuyến - Quarkus Microservices

Đồ án môn học phát triển hệ thống **Nhà thuốc trực tuyến** theo kiến trúc **Microservices** với **Java 21**, **Quarkus (3.39.4)**, **PostgreSQL 16 (Flyway)**, **MongoDB 7**, **Redis 7**, **Apache Kafka 3.8 (KRaft)**, **MinIO**, **MailHog**, **Jaeger OpenTelemetry** và **Python FastAPI**.

---

## 1. Danh sách Microservices, Cổng và Cơ sở dữ liệu

Mỗi service sở hữu một cơ sở dữ liệu riêng biệt (Database-per-Service), **tuyệt đối không đọc trực tiếp DB của nhau**, chỉ giao tiếp đồng bộ qua **REST Client** hoặc bất đồng bộ qua **Kafka**:

| STT | Tên Service | Cổng | Cơ sở dữ liệu | Tài khoản DB / Ghi chú |
| :--- | :--- | :---: | :--- | :--- |
| 1 | `api-gateway` | `8080` | *(Không dùng DB)* | Điều phối request, CORS, xác thực/chuyển tiếp tới các service |
| 2 | `medicine-service` | `8081` | **MongoDB**: `medicine_db` | Quản lý danh mục thuốc (OTC/ETC), tìm kiếm, đánh giá |
| 3 | `auth-service` | `8082` | **PostgreSQL**: `auth_db` + **Redis** | `auth_user` / `auth_pass` — JWT (RSA), phiên QR, quên mật khẩu |
| 4 | `prescription-service` | `8083` | **PostgreSQL**: `prescription_db` + **MinIO** | `prescription_user` / `prescription_pass` — Lưu ảnh đơn thuốc trên MinIO |
| 5 | `order-service` | `8084` | **PostgreSQL**: `order_db` + **Redis** | `order_user` / `order_pass` — Giỏ hàng Redis, snapshot giá/tên thuốc |
| 6 | `payment-service` | `8085` | **PostgreSQL**: `payment_db` | `payment_user` / `payment_pass` — COD, VNPay Sandbox, QR |
| 7 | `notification-service` | `8086` | **MongoDB**: `notification_db` | Nhận sự kiện Kafka, gửi email qua MailHog, đẩy thông báo WebSocket |
| 8 | `inventory-service` | `8087` | **PostgreSQL**: `inventory_db` | `inventory_user` / `inventory_pass` — Quản lý tồn kho (Nhóm 2) |
| 9 | `consultation-service` | `8088` | **MongoDB**: `consultation_db` | Tư vấn trực tuyến khách hàng - dược sĩ qua WebSocket (Nhóm 2) |
| 10 | `recommendation-service` | `8089` | **MongoDB**: `recommendation_db` | Gợi ý thuốc liên quan dựa trên lịch sử/sự kiện đơn hàng (Nhóm 2) |
| 11 | `face-recognition-service` | `8090` | **MongoDB**: `face_db` | Python FastAPI — Đăng ký và xác thực khuôn mặt |

---

## 2. Khởi chạy và Kiểm tra Hạ tầng (Docker Compose)

### 2.1. Bật toàn bộ container hạ tầng
Tại thư mục gốc `d:\pharmacy-microservices`, chạy:
```powershell
docker compose up -d
docker compose ps
```

### 2.2. Kiểm tra từng container hoạt động tốt
- **PostgreSQL (`localhost:5432`)**: kiểm tra danh sách 5 database đã được tạo bởi `docker/postgres/init.sql`:
  ```powershell
  docker exec -it pharmacy-postgres psql -U postgres -c "\l"
  docker exec -it pharmacy-postgres psql -U auth_user -d auth_db -c "SELECT current_database(), current_user;"
  ```
- **MongoDB (`localhost:27017`)**: kiểm tra phản hồi ping từ MongoDB:
  ```powershell
  docker exec -it pharmacy-mongodb mongosh --eval "db.runCommand({ ping: 1 })"
  ```
- **Redis (`localhost:6379`)**: kiểm tra lệnh `PING` (trả về `PONG`):
  ```powershell
  docker exec -it pharmacy-redis redis-cli ping
  ```
- **Kafka (`localhost:9092`)**: kiểm tra danh sách topic trên broker:
  ```powershell
  docker exec -it pharmacy-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
  ```
- **Giao diện Web quản trị hạ tầng**:
  - **MinIO Console**: http://localhost:9001 (`minioadmin` / `minioadmin`)
  - **MailHog (Xem email test)**: http://localhost:8025
  - **Jaeger UI (OpenTelemetry Tracing)**: http://localhost:16686

---

## 3. Hướng dẫn kết nối bằng DBeaver và MongoDB Compass

### 3.1. Kết nối PostgreSQL bằng DBeaver
1. Mở **DBeaver** -> Chọn **New Database Connection** -> Chọn **PostgreSQL**.
2. Nhập thông số kết nối (có thể kết nối bằng tài khoản quản trị `postgres` hoặc tài khoản riêng của từng service):
   - **Host**: `localhost`
   - **Port**: `5432`
   - **Database**: `auth_db` (hoặc `prescription_db`, `order_db`, `payment_db`, `inventory_db`)
   - **Username / Password**: `auth_user` / `auth_pass` (hoặc `postgres` / `postgres` rồi bật tích chọn *"Show all databases"* trong tab PostgreSQL).
3. Nhấn **Test Connection** -> **Finish**.

### 3.2. Kết nối MongoDB bằng MongoDB Compass
1. Mở **MongoDB Compass** -> Chọn **New Connection**.
2. Dán chuỗi kết nối (URI):
   ```text
   mongodb://localhost:27017
   ```
3. Nhấn **Connect**. Các database (`medicine_db`, `notification_db`, `consultation_db`, `recommendation_db`, `face_db`) sẽ tự động hiển thị ngay khi service ghi bản ghi đầu tiên.

---

## 4. Cách chạy từng Service (Quarkus Dev Mode)

Mở terminal riêng cho từng service cần chạy và thực thi `./mvnw quarkus:dev`:

```powershell
# Ví dụ chạy medicine-service (cổng 8081)
cd medicine-service
.\mvnw.cmd quarkus:dev

# Ví dụ chạy auth-service (cổng 8082)
cd auth-service
.\mvnw.cmd quarkus:dev
```

- **Swagger UI / OpenAPI** của mỗi service Quarkus khi chạy ở chế độ dev: `http://localhost:<cổng>/q/swagger-ui` (hoặc tải spec tại `/q/openapi`).

