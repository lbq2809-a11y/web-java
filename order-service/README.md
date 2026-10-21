# Order Service (Cổng 8084 - PostgreSQL `order_db` + Redis Giỏ Hàng)

Microservice quản lý giỏ hàng của khách hàng trên **Redis**, xử lý quy trình đặt hàng (**Checkout**), thẩm định điều kiện thuốc kê đơn (**ETC**) với `prescription-service`, kiểm tra giá thuốc thời gian thực với `medicine-service`, lưu **SNAPSHOT** đơn hàng, quản lý máy trạng thái (**State Machine**), lưu vết lịch sử (**Audit Trail**), xử lý hoàn trả / hủy đơn, và lắng nghe các sự kiện thanh toán từ `payment-service` qua **Apache Kafka** đảm bảo tính **Idempotent**.

---

## 1. Công nghệ & Cấu hình

- **Framework**: Java 21 + Quarkus 3.39.4 (`quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`, `quarkus-flyway`, `quarkus-redis-client`, `quarkus-rest-jackson`, `quarkus-rest-client-jackson`, `quarkus-smallrye-jwt`, `quarkus-smallrye-fault-tolerance`, `quarkus-messaging-kafka`, `quarkus-smallrye-openapi`).
- **Cơ sở dữ liệu**: PostgreSQL `localhost:5432` — Database: `order_db` (user: `order_user`, pass: `order_pass`).
- **Cache giỏ hàng**: Redis `localhost:6379` (Key format: `cart:{userId}`, TTL 7 ngày).
- **Messaging (Apache Kafka `localhost:9092`)**:
  - Outgoing: `order.created`, `order.status-changed`.
  - Incoming: `payment.completed`, `payment.failed` (Idempotent deduplication qua bảng `processed_events`).
- **Cổng HTTP**: `8084`.
- **Xác thực JWT**: SmallRye JWT xác thực bằng public key dùng chung `../keys/publicKey.pem` (thuật toán `RS256`).

---

## 2. Máy Trạng Thái Đơn Hàng (Order State Machine)

```
[CHO_XU_LY] --(Dược sĩ/Admin đóng gói)--> [DANG_GIAO] --(Dược sĩ/Admin giao thành công)--> [HOAN_THANH]
     |                                                                                             |
     +---(Khách hàng hủy / payment.failed)--> [DA_HUY]                                            +---(Khách yêu cầu đổi trả)--> [HOAN_TRA]
```

### Các bước nhảy hợp lệ:
1. `CHO_XU_LY -> DANG_GIAO`: Dược sĩ / Admin cập nhật khi chuẩn bị giao hàng.
2. `DANG_GIAO -> HOAN_THANH`: Dược sĩ / Admin cập nhật khi giao hàng thành công.
3. `CHO_XU_LY -> DA_HUY`: Khách hàng hủy đơn của chính mình, hoặc hệ thống hủy tự động khi nhận sự kiện `payment.failed`.
4. `HOAN_THANH -> HOAN_TRA`: Khách hàng yêu cầu hoàn trả đơn hàng đã nhận.

> Mọi bước nhảy trạng thái sai quy tắc (ví dụ `CHO_XU_LY -> HOAN_THANH`, hoặc `DANG_GIAO -> DA_HUY`, hoặc cập nhật trùng trạng thái) đều bị chặn tuyệt đối và trả về lỗi `400 Bad Request` kèm mã lỗi `INVALID_STATUS_TRANSITION`.

---

## 3. Hướng dẫn Khởi Chạy Service

### Bước 1: Khởi động hạ tầng Docker
Từ thư mục gốc `d:\pharmacy-microservices`:
```powershell
docker compose up -d postgres redis kafka
```

### Bước 2: Chạy `order-service` ở chế độ Dev
```powershell
cd order-service
.\mvnw.cmd quarkus:dev
```
Quarkus sẽ tự động:
1. Chạy Flyway migration:
   - `V1__init_orders.sql`: Tạo 4 bảng (`orders`, `order_items`, `shipping_addresses`, `order_status_history`).
   - `V2__add_processed_events.sql`: Tạo bảng `processed_events` phục vụ Idempotent Kafka Consumer.
2. Kết nối Redis tại `localhost:6379`.
3. Kết nối Kafka broker tại `localhost:9092`.
4. Khởi chạy giao diện Swagger UI tại: [http://localhost:8084/q/swagger-ui](http://localhost:8084/q/swagger-ui).

---

## 4. Danh Sách REST APIs

| Method | Endpoint | Quyền hạn (`Role`) | Mô tả |
| :--- | :--- | :---: | :--- |
| `POST` | `/api/v1/cart/items` | `KHACH_HANG` | Thêm thuốc vào giỏ hàng Redis |
| `GET` | `/api/v1/cart` | `KHACH_HANG` | Xem chi tiết giỏ hàng kèm giá thời gian thực từ `medicine-service` |
| `PUT` | `/api/v1/cart/items/{id}` | `KHACH_HANG` | Cập nhật số lượng thuốc trong giỏ |
| `DELETE`| `/api/v1/cart/items/{id}` | `KHACH_HANG` | Xóa 1 sản phẩm khỏi giỏ |
| `DELETE`| `/api/v1/cart` | `KHACH_HANG` | Xóa sạch toàn bộ giỏ hàng |
| `POST` | `/api/v1/orders/checkout` | `KHACH_HANG` | Đặt hàng từ giỏ (hỗ trợ `Idempotency-Key`, kiểm tra đơn thuốc ETC) |
| `GET` | `/api/v1/orders/my-orders`| `KHACH_HANG` | Xem danh sách đơn hàng của chính mình (phân trang & lọc trạng thái) |
| `GET` | `/api/v1/orders/{id}` | `KHACH_HANG`, `DUOC_SI`, `ADMIN` | Xem chi tiết đơn hàng (khách chỉ xem đơn của mình, dược sĩ/admin xem mọi đơn) |
| `PUT` | `/api/v1/orders/{id}/status` | `DUOC_SI`, `ADMIN` | Cập nhật trạng thái theo State Machine, phát Kafka `order.status-changed` |
| `POST` | `/api/v1/orders/{id}/cancel` | `KHACH_HANG`, `ADMIN` | Hủy đơn hàng khi đang ở trạng thái `CHO_XU_LY` |
| `POST` | `/api/v1/orders/{id}/refund` | `KHACH_HANG`, `ADMIN` | Yêu cầu hoàn trả đơn hàng khi đã ở trạng thái `HOAN_THANH` |
| `GET` | `/api/v1/orders/{id}/history`| `KHACH_HANG`, `DUOC_SI`, `ADMIN` | Xem lịch sử thay đổi trạng thái đơn hàng (Audit Trail) |

---

## 5. Chạy Toàn Bộ Kiểm Thử Tự Động (Integration Tests)

Chạy lệnh sau để thực thi toàn bộ **16 ca kiểm thử tích hợp**:
```powershell
cd order-service
.\mvnw.cmd test
```

### Bộ 16 ca kiểm thử bao phủ:
1. `testCase01_AddToCartAndGetCartDetail`: Thêm thuốc vào giỏ hàng Redis và xem chi tiết giỏ (tính giá thời gian thực).
2. `testCase02_UpdateQuantityAndRemoveItemFromCart`: Cập nhật số lượng và xóa thuốc khỏi giỏ hàng.
3. `testCase03_CheckoutEmptyCart_FailsWith400`: Checkout khi giỏ hàng rỗng $\to$ Bị từ chối `400 Bad Request` (`CART_EMPTY`).
4. `testCase04_CheckoutOTCSuccess_ClearsCart`: Đặt hàng đơn thuốc OTC $\to$ Nhận `201 Created`, lưu SNAPSHOT đầy đủ, xóa sạch giỏ hàng trên Redis, phát event Kafka `order.created`.
5. `testCase05_CheckoutETCWithoutPrescriptionId_FailsWith400`: Giỏ có thuốc kê đơn ETC nhưng thiếu `prescriptionId` $\to$ Bị từ chối `400 Bad Request` (`PRESCRIPTION_REQUIRED` kèm tên thuốc thiếu đơn).
6. `testCase06_CheckoutETCWithInvalidPrescription_FailsWith422`: Đơn thuốc không hợp lệ hoặc đã hết hạn $\to$ Bị từ chối `422 Unprocessable Entity` (`PRESCRIPTION_INVALID`).
7. `testCase07_CheckoutETCWithValidPrescription_Success`: Đặt hàng thuốc ETC kèm đơn thuốc hợp lệ $\to$ Nhận `201 Created`, gắn đơn thuốc vào đơn hàng thành công.
8. `testCase08_IdempotentCheckout_ReturnsSameOrder`: Gửi lại request checkout với cùng header `Idempotency-Key` $\to$ Trả lại đơn cũ đã tạo, không phát sinh đơn trùng.
9. `testCase09_SecurityAndRolePermissions`: Khách B xem đơn Khách A $\to$ Bị chặn `403 Forbidden`. Dược sĩ và Admin xem $\to$ `200 OK`.
10. `testCase10_ValidStatusTransitions`: Chuyển trạng thái hợp lệ `CHO_XU_LY -> DANG_GIAO -> HOAN_THANH` bởi Dược sĩ và Admin.
11. `testCase11_InvalidStatusTransitions_Blocked`: Chặn bước nhảy cóc (`CHO_XU_LY -> HOAN_THANH`), chặn trùng trạng thái, chặn Khách hàng gọi API cập nhật trạng thái (`403 Forbidden`).
12. `testCase12_CustomerCancelOrder_Success`: Khách hàng hủy đơn của mình khi đang `CHO_XU_LY` $\to$ `200 DA_HUY`. Chặn hủy lại đơn đã hủy.
13. `testCase13_CancelOrderInvalidStatusAndForbidden`: Chặn hủy đơn đã `HOAN_THANH` $\to$ `400 INVALID_STATUS_TRANSITION`. Khách B cố hủy đơn Khách A $\to$ `403 Forbidden`.
14. `testCase14_RefundOrder`: Hoàn trả đơn `HOAN_THANH` $\to$ `200 HOAN_TRA`. Chặn hoàn trả đơn đã hủy $\to$ `400 INVALID_STATUS_TRANSITION`. Khách B cố hoàn trả đơn Khách A $\to$ `403 Forbidden`.
15. `testCase15_GetOrderStatusHistory`: Tra cứu lịch sử chuyển trạng thái (Audit Trail) đầy đủ 4 bước theo trình tự thời gian.
16. `testCase16_PaymentKafkaConsumer_Idempotent`: Kafka Consumer nhận sự kiện `payment.completed` ghi audit log, nhận `payment.failed` tự động hủy đơn `CHO_XU_LY` $\to$ `DA_HUY` và phát `order.status-changed`. Gửi lại trùng `eventId` được xử lý Idempotent (không trùng lặp).
