# B2. auth-service (PostgreSQL + Redis)

Bước B2: tạo auth-service dùng PostgreSQL (auth_db) + Redis, cổng 8082. Làm theo từng giai đoạn, xong mỗi giai đoạn dừng lại để tôi kiểm tra.

Giai đoạn 1 - Cốt lõi: bảng users (id UUID, email unique, password_hash, họ tên, SĐT, role, trạng thái, created_at), migration Flyway; đăng ký, đăng nhập, sinh JWT (SmallRye JWT, khóa RSA, chứa userId + role, thời hạn hợp lý), refresh token, đăng xuất; phân quyền KHACH_HANG / DUOC_SI / ADMIN; API xem/sửa hồ sơ; ADMIN quản lý người dùng.

Giai đoạn 2 - Mật khẩu: đổi mật khẩu; quên mật khẩu (sinh token ngẫu nhiên lưu Redis có TTL, gửi email qua Kafka event tới notification-service hoặc tạm gửi trực tiếp bằng quarkus-mailer); đặt lại mật khẩu bằng token.

Giai đoạn 3 - Đăng nhập QR: web hiển thị QR chứa sessionId (lưu Redis TTL 2 phút, trạng thái PENDING); điện thoại/thiết bị đã đăng nhập quét và xác nhận (PENDING -> CONFIRMED); web nhận kết quả qua WebSocket (quarkus-websockets-next) và được cấp JWT. Vẽ sơ đồ luồng bằng chữ trước khi code.

Giai đoạn 4 - Face Login: auth-service gọi face-recognition-service qua REST Client. Luồng đăng ký khuôn mặt (upload ảnh, lưu vector) và đăng nhập bằng ảnh (so khớp, trả userId nếu độ giống vượt ngưỡng), kèm xử lý lỗi/timeout bằng Fault Tolerance. Nếu face-recognition-service chưa có, viết luôn bản Python FastAPI đơn giản (thư viện face_recognition hoặc DeepFace) với 2 endpoint /register và /verify, có Dockerfile, lưu vector vào MongoDB face_db.

Yêu cầu bảo mật: chống brute force đăng nhập (giới hạn số lần sai, dùng Redis), không log mật khẩu/token, trả thông báo lỗi chung chung khi sai email/mật khẩu.
Có test cho từng giai đoạn.
