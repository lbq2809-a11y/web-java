# Dịch Vụ Tư Vấn Thuốc Tự Động (consultation-service)

Dịch vụ vi mô đảm nhiệm chức năng tư vấn thuốc thông minh và quản lý lịch sử hội thoại cho hệ thống **Nhà thuốc trực tuyến**, chạy trên framework Quarkus, kết nối MongoDB `consultation_db`, cổng **8088**.

---

## 1. Kiến Trúc & Công Nghệ

- **Framework**: Quarkus 3.39.4 (Java 21).
- **Cơ sở dữ liệu**: MongoDB (`consultation_db`) qua `quarkus-mongodb-panache`.
  - `knowledge_chunks`: Lưu trữ các đoạn tri thức thuốc chuẩn hóa từ `medicine-service`.
  - `conversations`: Lưu trữ lịch sử hỏi đáp của từng người dùng (`userId`, `conversationId`).
- **Bảo mật & Phân quyền**: MicroProfile SmallRye JWT (`RS256`), hỗ trợ 3 vai trò: `KHACH_HANG`, `DUOC_SI`, `ADMIN`.
- **Giao tiếp liên service**: `quarkus-rest-client-jackson` gọi sang `medicine-service` (mặc định `http://localhost:8081`).
- **Chịu lỗi (Resilience)**: `quarkus-smallrye-fault-tolerance` đảm bảo `consultation-service` không crash khi `medicine-service` chưa khởi động.
- **Tầng 1 (Rule-based & Template)**:
  - Chuẩn hóa văn bản tiếng Việt (Unicode NFD bỏ dấu).
  - So khớp tên thuốc & hoạt chất chính xác và khớp mờ Levenshtein chịu lỗi chính tả nhẹ (ngưỡng 0.8).
  - Phân loại ý định qua `synonyms.json`: `THANH_PHAN`, `CHI_DINH`, `CHONG_CHI_DINH`, `CACH_DUNG`, `LOAI_THUOC`, `GIA`.
  - Điền mẫu trả lời qua `templates.json`, tự động gắn cảnh báo bắt buộc có đơn bác sĩ với thuốc kê đơn (`ETC`) và disclaimer y tế.
  - Bộ lọc an toàn: Cảnh báo ngay khi phát hiện từ khóa cấp cứu 115, tự hại, mang thai, trẻ sơ sinh.
- **Giới hạn tần suất (Rate Limiting)**: Tối đa 20 yêu cầu / 60 giây mỗi người dùng (trả về HTTP 429).
- **Giới hạn độ dài câu hỏi**: Tối đa 500 ký tự (trả về HTTP 400).
- **Bảo mật thông tin y tế**: Tuyệt đối không log nội dung câu hỏi người dùng ở mức `INFO`.

---

## 2. Cách Khởi Động & Chạy Kiểm Thử

### Yêu cầu tiên quyết
Đảm bảo container MongoDB đang chạy trên máy (port 27017):
```bash
docker compose up -d pharmacy-mongodb
```

### Chạy chế độ phát triển (Dev mode)
```bash
cd consultation-service
./mvnw quarkus:dev
```
- Swagger UI: [http://localhost:8088/q/swagger-ui/](http://localhost:8088/q/swagger-ui/)
- OpenAPI Spec: [http://localhost:8088/q/openapi](http://localhost:8088/q/openapi)
- Dev UI: [http://localhost:8088/q/dev/](http://localhost:8088/q/dev/)

### Chạy bộ kiểm thử tự động
```bash
./mvnw test
```

---

## 3. Danh Sách API Chính

| Method | Endpoint | Quyền hạn | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/consultations/ask` | `KHACH_HANG`, `DUOC_SI`, `ADMIN` | Đặt câu hỏi tư vấn thuốc (Tầng 1 & An toàn) |
| `GET` | `/api/v1/consultations/conversations` | `KHACH_HANG`, `DUOC_SI`, `ADMIN` | Danh sách tóm tắt phiên hội thoại của người dùng |
| `GET` | `/api/v1/consultations/conversations/{id}` | `KHACH_HANG`, `DUOC_SI`, `ADMIN` | Chi tiết một phiên hội thoại (người dùng chỉ xem của mình) |
| `DELETE` | `/api/v1/consultations/conversations/{id}` | `KHACH_HANG`, `DUOC_SI`, `ADMIN` | Xóa phiên hội thoại (người dùng chỉ xóa của mình) |
| `POST` | `/api/v1/consultations/admin/reindex` | `ADMIN` | Nạp lại toàn bộ tri thức thuốc từ medicine-service |
| `GET` | `/api/v1/dev/tokens` | Public *(Chỉ bật ở profile dev/test)* | Tiện ích sinh JWT token thử nghiệm |

---

## 4. Hướng Dẫn Thử Nghiệm Nhanh

### Bước 1: Lấy Token Thử Nghiệm (Profile Dev)
```bash
curl -X GET "http://localhost:8088/api/v1/dev/tokens?role=KHACH_HANG"
```

### Bước 2: Gửi Câu Hỏi Tư Vấn
```bash
curl -X POST "http://localhost:8088/api/v1/consultations/ask" \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Thuốc Panadol Extra uống như thế nào?"
  }'
```

### Bước 3: Nạp Lại Tri Thức Thuốc (Role ADMIN)
```bash
# Lấy admin token
ADMIN_TOKEN=$(curl -s "http://localhost:8088/api/v1/dev/tokens?role=ADMIN" | jq -r .token)

# Gọi reindex
curl -X POST "http://localhost:8088/api/v1/consultations/admin/reindex" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
