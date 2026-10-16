# Dự án: Nhà thuốc trực tuyến - Quarkus Microservices (đồ án môn học)

## Công nghệ bắt buộc
Java 21, Quarkus (bản ổn định mới nhất, ghi rõ trong pom.xml), Maven.
REST: quarkus-rest + quarkus-rest-jackson, OpenAPI: quarkus-smallrye-openapi.
JWT: SmallRye JWT (tự viết auth-service, KHÔNG dùng Keycloak), mật khẩu băm BCrypt. Hợp đồng claims và khóa RSA dùng chung xem tại `docs/JWT-CONTRACT.md`.
PostgreSQL: Hibernate ORM Panache + Flyway (không để Hibernate tự sinh bảng).
MongoDB: quarkus-mongodb-panache. Redis: quarkus-redis-client.
Kafka: quarkus-messaging-kafka. Email: quarkus-mailer. WebSocket: quarkus-websockets-next.
Resilience: smallrye-fault-tolerance. Tracing: quarkus-opentelemetry.
Test: JUnit5, RestAssured, Testcontainers.

## Hạ tầng (đã có docker-compose.yml, chạy: docker compose up -d)
Service Quarkus chạy trên máy (./mvnw quarkus:dev), kết nối tới container qua localhost.
- PostgreSQL localhost:5432. Mỗi service một DB và user riêng:
  auth_db (auth_user/auth_pass), prescription_db (prescription_user/prescription_pass),
  order_db (order_user/order_pass), payment_db (payment_user/payment_pass),
  inventory_db (inventory_user/inventory_pass)
- MongoDB localhost:27017 (không mật khẩu): medicine_db, notification_db, consultation_db, recommendation_db, face_db
- Redis localhost:6379 | Kafka localhost:9092 | MinIO localhost:9000 (minioadmin/minioadmin)
- MailHog SMTP localhost:1025 (xem email: http://localhost:8025) | Jaeger http://localhost:16686

## Service, cổng, CSDL (mỗi service một CSDL riêng, KHÔNG đọc DB của nhau; giao tiếp qua REST hoặc Kafka)
- api-gateway 8080: không có DB
- medicine-service 8081: MongoDB medicine_db
- auth-service 8082: PostgreSQL auth_db + Redis (phiên QR, token đặt lại mật khẩu)
- prescription-service 8083: PostgreSQL prescription_db (ảnh lưu MinIO, DB chỉ lưu đường dẫn)
- order-service 8084: PostgreSQL order_db + Redis (giỏ hàng)
- payment-service 8085: PostgreSQL payment_db
- notification-service 8086: MongoDB notification_db
- inventory-service 8087 (nhóm 2): PostgreSQL inventory_db
- consultation-service 8088 (nhóm 2): MongoDB consultation_db
- recommendation-service 8089 (nhóm 2): MongoDB recommendation_db
- face-recognition-service 8090: Python FastAPI, MongoDB face_db

## Nghiệp vụ cốt lõi
3 vai trò: KHACH_HANG, DUOC_SI, ADMIN.
Thuốc kê đơn (ETC) chỉ được mua khi có đơn thuốc đã được dược sĩ duyệt. Thuốc không kê đơn (OTC) mua tự do.
Đơn hàng lưu SNAPSHOT tên/giá thuốc. Trạng thái đơn: CHO_XU_LY, DANG_GIAO, HOAN_THANH, DA_HUY, HOAN_TRA.
Thanh toán: COD, VNPay sandbox, QR. Callback phải kiểm tra chữ ký và xử lý idempotent.

## Quy ước code
- Package: resource / service / repository / entity (hoặc document) / dto / mapper / exception
- API: /api/v1/<danh-từ-số-nhiều>. Không trả entity trực tiếp, dùng DTO + Bean Validation.
- Lỗi trả thống nhất: {"code": "...", "message": "...", "details": [...]}
- PostgreSQL: id UUID, created_at/updated_at, tiền NUMERIC(19,2), migration tại src/main/resources/db/migration/V1__*.sql
- Không hard-code mật khẩu/khóa bí mật: dùng application.properties + biến môi trường.
- Mỗi service có: Dockerfile, README (cách chạy), OpenAPI, test pass.
- Code và tên biến tiếng Anh; comment giải thích bằng tiếng Việt.

## Nguyên tắc làm việc với tôi
- Chỉ làm đúng việc tôi giao trong tin nhắn hiện tại. KHÔNG làm sang service/bước khác, KHÔNG tự thêm tính năng.
- KHÔNG tự đổi công nghệ, phiên bản, cổng hoặc tên DB đã ghi ở trên.
- Yêu cầu mơ hồ thì hỏi tối đa 3 câu ngắn trước khi code.
- Không viết code giả/placeholder kiểu "TODO tự làm". Thiếu thông tin thì nói rõ.
- Luôn chạy test và báo kết quả thật. Không nói "đã chạy được" nếu chưa chạy.
- Không chạy lệnh xóa dữ liệu hoặc xóa volume Docker (docker system prune, docker compose down -v) khi chưa hỏi tôi.
- Cuối mỗi bước: tóm tắt cách chạy/kiểm tra và giải thích ngắn "vì sao thiết kế như vậy" để tôi trả lời được thầy.
- Nếu yêu cầu của tôi mâu thuẫn hoặc sai thực tế, hãy nói thẳng.
