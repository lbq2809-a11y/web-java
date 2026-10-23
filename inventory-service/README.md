# Inventory Service - Nhà Thuốc Trực Tuyến

Service quản lý kho dược phẩm, theo dõi lô thuốc (Batches), giữ hàng theo nguyên tắc **FEFO (First-Expired, First-Out)**, kiểm soát biến động kho (Stock Movements), quản lý cảnh báo tồn kho và phát sự kiện qua **Transactional Outbox Pattern**.

- **Cổng HTTP**: `8087` (Swagger UI: `http://localhost:8087/q/swagger-ui`)
- **Cơ sở dữ liệu**: PostgreSQL `inventory_db` (user: `inventory_user`, password: `inventory_pass`)
- **Xác thực**: SmallRye JWT (RS256 với cặp khóa RSA `keys/publicKey.pem` và `keys/privateKey.pem`)
- **Event-Driven**: Apache Kafka (`localhost:9092`)

---

## 1. Cách Chạy Service

### Chế độ phát triển (Dev Mode):
```bash
cd inventory-service
./mvnw quarkus:dev
```

### Chạy kiểm thử tự động (Unit & Integration Tests):
```bash
cd inventory-service
./mvnw test
```
*(Bộ test chạy trên H2 in-memory và SmallRye In-Memory Kafka, độc lập hoàn toàn với hạ tầng Docker bên ngoài).*

---

## 2. Danh Sách API Quản Trị & Nghiệp Vụ

Tất cả các API yêu cầu JWT Header `Authorization: Bearer <token>` với vai trò `ADMIN` hoặc `DUOC_SI`. (Có thể sinh nhanh token thử nghiệm tại `GET /api/v1/dev/tokens?role=ADMIN`).

| Method | Endpoint | Mô tả |
| :--- | :--- | :--- |
| `POST` | `/api/v1/batches` | Nhập lô thuốc mới (Validation ngày hết hạn ở tương lai, số lượng > 0) |
| `GET` | `/api/v1/batches` | Danh sách các lô (hỗ trợ lọc `medicineId`, `nearExpiryDays`, phân trang `page`, `size`) |
| `GET` | `/api/v1/batches/{id}` | Chi tiết một lô thuốc theo UUID |
| `GET` | `/api/v1/stocks/{medicineId}` | Tổng tồn khả dụng (chỉ tính lô còn hạn) và chi tiết các lô của thuốc |
| `GET` | `/api/v1/stocks` | Báo cáo tồn kho toàn bộ thuốc trong hệ thống |
| `POST` | `/api/v1/stocks/adjust` | Điều chỉnh tồn kho lô (kiểm kê, hỏng vỡ) kèm lý do bắt buộc |
| `POST` | `/api/v1/stocks/check` | API kiểm tra tồn kho hàng loạt cho danh sách mặt hàng |
| `GET` | `/api/v1/stocks/alerts` | Danh sách cảnh báo hiện tại (lô sắp hết hạn và thuốc dưới ngưỡng tồn) |
| `GET` | `/api/v1/stock-movements` | Sổ cái biến động kho (IMPORT, EXPORT, RESERVE, RELEASE, ADJUST) |
| `GET` | `/api/v1/dev/tokens` | Sinh token thử nghiệm cho KHACH_HANG, DUOC_SI, ADMIN (Chỉ bật ở Dev/Test) |

---

## 3. Thử Nghiệm Bằng Tay Qua Kafka (Hai Lệnh Mẫu)

### Bước 1: Gửi event `order.created` giả lập vào Kafka
Mở terminal và chạy lệnh `kafka-console-producer` trong container Kafka hoặc máy trạm:
```bash
docker exec -i pharmacy-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic order.created \
  --property "parse.key=true" \
  --property "key.separator=:"
```
Dán dòng sau (Key là `orderId`, cách nhau bởi dấu `:`):
```json
990e8400-e29b-41d4-a716-446655440222:{"eventId":"111e8400-e29b-41d4-a716-446655440001","eventType":"order.created","occurredAt":"2026-10-24T10:00:00Z","correlationId":"corr-test-manual","orderId":"990e8400-e29b-41d4-a716-446655440222","customerId":"550e8400-e29b-41d4-a716-446655440000","customerEmail":"khachhang@pharmacy.local","customerName":"Nguyễn Văn A","status":"CHO_XU_LY","totalAmount":570000.00,"items":[{"medicineId":"6ad23be6c034632667baaa01","medicineName":"Paracetamol 500mg","prescriptionType":"OTC","unitPrice":5000.00,"quantity":2}]}
```

### Bước 2: Quan sát kết quả giữ hàng trong topic `inventory.reserved`
Mở một terminal khác để lắng nghe kết quả phản hồi:
```bash
docker exec -i pharmacy-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic inventory.reserved \
  --from-beginning \
  --property print.key=true
```
Bạn sẽ thấy thông điệp trả về xác nhận giữ hàng thành công:
```json
990e8400-e29b-41d4-a716-446655440222	{"eventId":"...","eventType":"inventory.reserved","occurredAt":"...","correlationId":"corr-test-manual","orderId":"990e8400-e29b-41d4-a716-446655440222","items":[{"medicineId":"6ad23be6c034632667baaa01","quantity":2}]}
```

---

## 4. Các Nguyên Tắc Thiết Kế Trọng Yếu

1. **FEFO (First-Expired, First-Out)**: Khi đơn hàng tới, hệ thống luôn chọn lô có ngày hết hạn gần nhất (`ORDER BY expiry_date ASC, received_at ASC`) để xuất trước, giảm thiểu rủi ro thuốc hết hạn trong kho. Lô đã hết hạn (`expiry_date <= CURRENT_DATE`) sẽ bị loại bỏ hoàn toàn.
2. **Chống Deadlock Concurrency**: Danh sách `medicineId` trong đơn hàng bắt buộc được sắp xếp theo thứ tự bảng chữ cái tăng dần trước khi thực hiện khóa dòng (`SELECT ... FOR UPDATE`), đảm bảo mọi giao dịch đồng thời luôn khóa tài nguyên theo cùng một thứ tự và triệt tiêu khả năng xảy ra chu trình Deadlock.
3. **All-or-Nothing**: Đơn hàng chỉ giữ kho nếu mọi mặt hàng đều có đủ tồn; nếu thiếu bất kỳ mặt hàng nào thì không giữ gì cả và phát event `inventory.reservation-failed`.
4. **Transactional Outbox Pattern**: Bản ghi Outbox được lưu vào CSDL cùng transaction với việc cập nhật tồn kho. Background Scheduler quét và đẩy lên Kafka định kỳ, đảm bảo At-Least-Once Delivery và tránh lỗi Dual-Write.
