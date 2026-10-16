# B7. api-gateway

Bước B7: tạo api-gateway, cổng 8080.
Quarkus không có gateway dựng sẵn, nên hãy nêu ngắn gọn 2 phương án (A: một service Quarkus tự viết dùng REST Client/Vert.x proxy; B: Nginx hoặc Traefik trong Docker Compose) kèm ưu nhược điểm, rồi làm phương án A (trừ khi tôi đổi ý).
Yêu cầu:
- Định tuyến: /api/v1/auth/** -> auth-service; /api/v1/medicines/** và /api/v1/categories/** -> medicine-service; /api/v1/prescriptions/** -> prescription-service; /api/v1/cart/** và /api/v1/orders/** -> order-service; /api/v1/payments/** -> payment-service; /api/v1/notifications/** -> notification-service.
- Kiểm tra JWT ở gateway cho các route cần đăng nhập; cho phép route công khai (đăng nhập, đăng ký, xem thuốc).
- Chuyển tiếp header Authorization và correlation-id; cấu hình CORS.
- Xử lý lỗi khi service đích không phản hồi (503 có thông báo rõ); health check.
- File cấu hình định tuyến dễ sửa.
