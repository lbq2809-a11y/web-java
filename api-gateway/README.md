# API Gateway - Cổng Tiếp Nhận Tập Trung (Pharmacy Microservices)

API Gateway là cổng tiếp nhận tập trung (Reverse Proxy) duy nhất public ra ngoài (cổng **8080**) cho các client (Web Frontend React/Vue, Mobile App, Postman), phân phối và điều hướng yêu cầu tới các microservices backend nội bộ.

---

## 1. Kiến Trúc & Tính Năng Kỹ Thuật

- **Framework**: Java 21, Quarkus 3.x, Maven.
- **Cơ chế Reverse Proxy**: Eclipse Vert.x Web Router kết hợp Vert.x `HttpClient` Reactive Streaming Non-blocking.
- **Xử lý Stream Body (`pipeTo`)**: Stream trực tiếp dữ liệu dạng nhị phân theo từng chunk TCP (zero memory-buffering), bảo toàn 100% boundary và magic bytes cho các request upload ảnh Multipart Form Data sang `prescription-service`.
- **Tối ưu HTTP GET/HEAD**: Request `GET`/`HEAD` không có body được đóng socket kết thúc ngay (`clientReq.end()`), không pipe stream rỗng, tránh rủi ro treo socket keep-alive trên backend Quarkus.
- **Bảo mật & Xác thực**: SmallRye JWT, xác minh chữ ký RSA-2048 bằng khóa công khai dùng chung `../keys/publicKey.pem`.
- **Chống giả mạo Header (Header Sanitization)**: Lọc sạch các header nhạy cảm do client tự ý gửi lên (`X-User-*`, `X-Role`, `X-Roles`, `X-Customer-*`, `X-Forwarded-User`) trước khi chuyển tiếp sang backend.
- **Truy vết phân tán (Distributed Tracing)**: Tự động sinh mới UUID `X-Correlation-Id` nếu client chưa gửi, hoặc chuyển tiếp nguyên vẹn nếu client đã có; đồng bộ với OpenTelemetry / Jaeger.
- **Xử lý lỗi phân biệt chuẩn RFC**: Trả về `503 SERVICE_UNAVAILABLE` khi backend down (kết nối thất bại) và `504 GATEWAY_TIMEOUT` khi backend phản hồi quá hạn (timeout).
- **CORS**: Cho phép các nguồn gốc localhost từ frontend dev server (`localhost:3000`, `localhost:5173`, `localhost:8080`).

---

## 2. Bảng Định Tuyến Dịch Vụ (Routing Table)

| STT | Route Prefix | Backend Service | Cổng Mặc Định | Headers Chuyển Tiếp Đặc Thù |
| :---: | :--- | :--- | :--- | :--- |
| 1 | `/api/v1/auth/**` | `auth-service` | `8082` | `Authorization`, `X-Correlation-Id` |
| 2 | `/api/v1/medicines/**`<br>`/api/v1/categories/**` | `medicine-service` | `8081` | `Authorization`, `X-Correlation-Id` |
| 3 | `/api/v1/prescriptions/**` | `prescription-service` | `8083` | `Authorization`, `X-Correlation-Id`, **Multipart Streaming** |
| 4 | `/api/v1/cart/**` | `order-service` | `8084` | `Authorization`, `X-Correlation-Id` |
| 5 | `/api/v1/orders/**` | `order-service` | `8084` | `Authorization`, `X-Correlation-Id`, `Idempotency-Key` |
| 6 | `/api/v1/payments/**` | `payment-service` | `8085` | `Authorization`, `X-Correlation-Id` |
| 7 | `/api/v1/notifications/**`| `notification-service` | `8086` | `Authorization`, `X-Correlation-Id` |

---

## 3. Danh Sách Các Tuyến Đường Công Khai (Public Routes - Không Cần JWT)

Các route sau được Gateway cho phép đi qua tự do mà không yêu cầu header `Authorization`:

1. `POST /api/v1/auth/login` (Đăng nhập bằng email/mật khẩu)
2. `POST /api/v1/auth/register` (Đăng ký tài khoản khách hàng)
3. `POST /api/v1/auth/refresh-token` (Làm mới access token)
4. `POST /api/v1/auth/forgot-password` (Yêu cầu mã đặt lại mật khẩu)
5. `POST /api/v1/auth/reset-password` (Đặt lại mật khẩu)
6. `POST /api/v1/auth/qr/session` (Tạo phiên đăng nhập QR mới)
7. `GET /api/v1/auth/qr/session/{sessionId}` (Kiểm tra trạng thái phiên QR)
8. `GET /api/v1/medicines/**` & `GET /api/v1/categories/**` (Tra cứu danh mục và thông tin thuốc)
9. `GET /api/v1/payments/vnpay/**` & `POST /api/v1/payments/vnpay/**` (VNPay IPN webhook & Return URL đối soát)
10. `GET /q/**` (Quarkus Health Check `/q/health`, OpenAPI `/q/openapi`, Swagger UI)
11. `OPTIONS *` (CORS Preflight requests)

> [!IMPORTANT]
> **Quy định bảo mật QR**:
> - API quét xác nhận QR từ thiết bị di động (`POST /api/v1/auth/qr/confirm`) **BẮT BUỘC phải có JWT hợp lệ**.

---

## 4. Ghi Chú Quan Trọng Về WebSocket

> [!NOTE]
> **API Gateway chỉ đóng vai trò HTTP/REST Reverse Proxy**, không thực hiện trung chuyển kết nối WebSocket.
> Đối với các tính năng giao tiếp thời gian thực qua giao thức WebSocket (WebSocket-Next):
> - **Đăng nhập QR** (Lắng nghe kết quả xác nhận): Client kết nối trực tiếp tới `ws://localhost:8082/...` (`auth-service`).
> - **Chat tư vấn trực tuyến với dược sĩ**: Client kết nối trực tiếp tới `ws://localhost:8088/...` (`consultation-service`).

---

## 5. Hướng Dẫn Khởi Chạy Ứng Dụng

### 5.1. Chạy chế độ Dev (Live reload)
```bash
cd api-gateway
./mvnw quarkus:dev
```
API Gateway sẽ khởi động tại: `http://localhost:8080`.
Tài liệu OpenAPI & Swagger UI tương tác: `http://localhost:8080/q/swagger-ui`

### 5.2. Chạy kiểm thử tự động
```bash
cd api-gateway
./mvnw test
```
Bộ test `GatewayRoutingTest` tự động giả lập các cổng backend và kiểm thử toàn diện mọi kịch bản định tuyến, bảo mật, chuẩn hóa đường dẫn, lọc header và mã lỗi.
