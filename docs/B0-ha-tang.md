# B0. Kiểm tra hạ tầng (docker-compose.yml đã được cung cấp sẵn)

Bước B0: kiểm tra và hoàn thiện hạ tầng, CHƯA viết service nào.
1. Đọc docker-compose.yml và docker/postgres/init.sql. Báo lại nếu thấy lỗi cấu hình hoặc xung đột với GEMINI.md.
2. Hướng dẫn tôi chạy `docker compose up -d`, kiểm tra từng container chạy tốt (lệnh cụ thể cho Postgres, MongoDB, Redis, Kafka).
3. Hướng dẫn kết nối thử bằng DBeaver (Postgres) và MongoDB Compass (Mongo).
4. Tạo README.md ở thư mục gốc: giới thiệu dự án, danh sách service + cổng, cách bật hạ tầng, cách chạy từng service.
5. Tạo cấu trúc thư mục rỗng cho các service (medicine-service, auth-service, prescription-service, order-service, payment-service, notification-service, api-gateway).
