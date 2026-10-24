# Recommendation Service - Nhà Thuốc Trực Tuyến

Service gợi ý thuốc thông minh dựa trên thống kê mua hàng (Frequently Bought Together, Personalized Recommendations, Related by Category) và tuân thủ nghiêm ngặt **Quy Chuẩn An Toàn Dược Phẩm**.

- **Cổng HTTP**: `8089` (Swagger UI: `http://localhost:8089/q/swagger-ui`)
- **Cơ sở dữ liệu**: MongoDB `recommendation_db` (`localhost:27017`)
- **Xác thực**: SmallRye JWT (RS256 với cặp khóa RSA `keys/publicKey.pem` và `keys/privateKey.pem`)
- **Event-Driven**: Apache Kafka (`localhost:9092`), lắng nghe topic `order.created` và `order.status-changed`

---

## 1. Cách Chạy Service

### Chế độ phát triển (Dev Mode):
```bash
cd recommendation-service
./mvnw quarkus:dev
```
*(Trong chế độ dev, service tự động tạo các chỉ mục MongoDB và nạp sẵn ~30 đơn hàng giả lập để bạn có thể xem ngay kết quả gợi ý trên Swagger UI).*

### Chạy kiểm thử tự động (Unit & Integration Tests):
```bash
cd recommendation-service
./mvnw test
```

---

## 2. Danh Sách API Gợi Ý

| Method | Endpoint | Quyền | Mô tả |
| :--- | :--- | :---: | :--- |
| `GET` | `/api/v1/recommendations/also-bought/{medicineId}?limit=5` | Public | Thuốc thường mua kèm nhiều nhất (hỗ trợ ETC kèm `requiresPrescription=true`) |
| `GET` | `/api/v1/recommendations/for-me?limit=5` | JWT | Gợi ý cá nhân hóa. **TUYỆT ĐỐI KHÔNG GỢI Ý ETC**, fallback sang Best Sellers cho user mới |
| `GET` | `/api/v1/recommendations/related/{medicineId}?limit=5` | Public | Gợi ý các thuốc cùng danh mục điều trị (`categoryId`) |
| `GET` | `/api/v1/dev/tokens?role=KHACH_HANG` | Dev/Test | Sinh JWT token thử nghiệm để dán vào nút Authorize trên Swagger UI |
| `POST` | `/api/v1/dev/seed` | Dev/Test | Kích hoạt nạp dữ liệu mẫu 30 đơn hàng giả lập |

---

## 3. Lệnh Thử Nghiệm Bằng Tay (Kafka CLI & cURL)

### Gửi sự kiện `order.created` giả lập vào Kafka:
```bash
docker exec -i pharmacy-kafka kafka-console-producer --bootstrap-server localhost:9092 --topic order.created << 'EOF'
{"eventId":"11111111-1111-1111-1111-111111111111","eventType":"order.created","occurredAt":"2026-10-24T10:00:00Z","correlationId":"corr-test","orderId":"99999999-9999-9999-9999-999999999999","customerId":"550e8400-e29b-41d4-a716-446655440000","items":[{"medicineId":"6ad23be6c034632667baaa02","medicineName":"Panadol Extra","unitPrice":55000,"quantity":2},{"medicineId":"6ad23be6c034632667baaa03","medicineName":"Vitamin C","unitPrice":45000,"quantity":1}]}
EOF
```

### Gửi sự kiện `order.status-changed` (HOAN_THANH) để chốt thống kê:
```bash
docker exec -i pharmacy-kafka kafka-console-producer --bootstrap-server localhost:9092 --topic order.status-changed << 'EOF'
{"eventId":"22222222-2222-2222-2222-222222222222","eventType":"order.status-changed","occurredAt":"2026-10-24T10:05:00Z","correlationId":"corr-test","orderId":"99999999-9999-9999-9999-999999999999","customerId":"550e8400-e29b-41d4-a716-446655440000","oldStatus":"DANG_GIAO","newStatus":"HOAN_THANH","reason":"Đơn hàng giao thành công"}
EOF
```

### Kiểm tra kết quả gợi ý mua kèm qua cURL:
```bash
curl -s http://localhost:8089/api/v1/recommendations/also-bought/6ad23be6c034632667baaa02 | jq .
```
