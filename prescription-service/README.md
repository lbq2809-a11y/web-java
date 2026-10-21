# Prescription Service (Cổng 8083 - PostgreSQL `prescription_db` + MinIO S3)

Microservice quản lý việc tải ảnh đơn thuốc của khách hàng, lưu trữ ảnh trên **MinIO**, quy trình thẩm định duyệt/từ chối của Dược sĩ, phát sự kiện **Kafka `prescription.reviewed`**, và cung cấp các API nội bộ kiểm tra tính hợp lệ của đơn thuốc cho **`order-service`**.

---

## 1. Công nghệ & Cấu hình

- **Framework**: Java 21 + Quarkus 3.39.4 (`quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`, `quarkus-flyway`, `quarkus-rest-jackson`, `quarkus-smallrye-jwt`, `quarkus-smallrye-openapi`, `io.minio:minio`, `quarkus-messaging-kafka`).
- **Cơ sở dữ liệu**: PostgreSQL `localhost:5432` — Database: `prescription_db` (user: `prescription_user`, pass: `prescription_pass`).
- **Lưu trữ ảnh**: MinIO S3 `http://localhost:9000` (Console: `http://localhost:9001` - user: `minioadmin`, pass: `minioadmin`). Bucket: `prescriptions` (tự động khởi tạo).
- **Messaging**: Apache Kafka `localhost:9092` — Topic: `prescription.reviewed`.
- **Cổng HTTP**: `8083`.
- **Xác thực JWT**: SmallRye JWT sử dụng public key dùng chung `../keys/publicKey.pem` (thuật toán `RS256`).

---

## 2. Hướng dẫn Khởi Chạy Service

### Bước 1: Khởi động các hạ tầng Docker (PostgreSQL, MinIO, Kafka)
Từ thư mục gốc `d:\pharmacy-microservices`:
```powershell
docker compose up -d postgres kafka minio
```

### Bước 2: Chạy `prescription-service` ở chế độ Dev
```powershell
cd prescription-service
.\mvnw.cmd quarkus:dev
```
Quarkus sẽ tự động:
1. Chạy Flyway migration `V1__init_prescriptions.sql` tạo 3 bảng (`prescriptions`, `prescription_items`, `prescription_order_links`).
2. Kết nối MinIO tại `localhost:9000` và tự động tạo bucket `prescriptions` nếu chưa có.
3. Kích hoạt giao diện Swagger UI tại: [http://localhost:8083/q/swagger-ui](http://localhost:8083/q/swagger-ui).

---

## 3. Hướng dẫn Thử Nghiệm Trên Swagger UI (Kèm Phân Quyền)

### Bước 1: Lấy Token Thử Nghiệm
Trên Swagger UI tại [http://localhost:8083/q/swagger-ui](http://localhost:8083/q/swagger-ui), cuộn xuống mục **`Dev Token Generator (Dev/Test Only)`** -> mở `GET /api/v1/dev/tokens`:

1. **Khách hàng (`KHACH_HANG`)**:
   - Nhập `role = KHACH_HANG` -> Bấm **Execute** -> Copy chuỗi `token`.
   - Bấm nút **Authorize** ở góc trên cùng bên phải, dán token vào ô **Value** -> Bấm **Authorize** -> **Close**.
   - Thử nghiệm:
     - `POST /api/v1/prescriptions` (Multipart): Tải lên ảnh đơn thuốc (JPG hoặc PNG $\le 5\text{MB}$), nhận mã đơn mới ở trạng thái `CHO_DUYET`.
     - `GET /api/v1/prescriptions/my-prescriptions`: Xem danh sách đơn của mình.
     - `GET /api/v1/prescriptions/{id}/image`: Xem trực tiếp ảnh đơn thuốc.

2. **Dược sĩ (`DUOC_SI`)**:
   - Lấy token với `role = DUOC_SI` -> Bấm **Authorize** -> Logout token cũ và dán token mới.
   - Thử nghiệm:
     - `GET /api/v1/prescriptions/pending`: Xem danh sách đơn đang chờ duyệt.
     - `PUT /api/v1/prescriptions/{id}/approve`: Duyệt đơn, nhập danh mục thuốc được phép mua (`medicineId`, `maxAllowedQuantity`) và hạn đơn (`expiresAt`).
     - `PUT /api/v1/prescriptions/{id}/reject`: Từ chối đơn (bắt buộc nhập `reason`).

3. **Kiểm tra API nội bộ cho Order Service**:
   - `POST /api/v1/prescriptions/{id}/validate-for-order`: Kiểm tra đơn thuốc có hợp lệ cho giỏ hàng ETC không.
   - `POST /api/v1/prescriptions/{id}/order-links`: Gắn đơn thuốc vào `orderId` (Idempotent).

---

## 4. Kiểm tra Ảnh trong MinIO Console

1. Mở trình duyệt vào giao diện web MinIO Console: [http://localhost:9001](http://localhost:9001).
2. Đăng nhập với tài khoản:
   - **Username**: `minioadmin`
   - **Password**: `minioadmin` (hoặc `minioadmin123`)
3. Vào mục **Buckets** -> Chọn bucket **`prescriptions`**.
4. Bạn sẽ thấy các tệp ảnh được phân theo thư mục dạng `prescriptions/{year}/{month}/{UUID}.jpg` hoặc `.png`.

---

## 5. Chạy Bộ Kiểm Thử Tự Động (Integration Tests)

Chạy lệnh sau để thực thi toàn bộ 11 ca kiểm thử tích hợp:
```powershell
cd prescription-service
.\mvnw.cmd test
```

### Bộ 11 ca kiểm thử bao phủ:
1. `testCase01_CustomerUploadsPrescriptionSuccessfully`: Khách hàng upload ảnh đơn thuốc JPG thành công -> `201 Created` (`CHO_DUYET`).
2. `testCase02_UploadInvalidFileType_FailsWith400`: Upload sai định dạng (tệp text, magic bytes sai) -> `400 Bad Request` (`INVALID_FILE_TYPE`).
3. `testCase03_UploadFileSizeExceeded_FailsWith400`: Upload file vượt quá 5MB -> `400 Bad Request` (`FILE_SIZE_EXCEEDED`).
4. `testCase04_CustomerCannotViewAnotherCustomerPrescription`: Khách hàng B cố xem đơn của Khách hàng A -> `403 Forbidden`.
5. `testCase05_PharmacistCanViewPendingListAndDetails`: Dược sĩ xem danh sách chờ duyệt, chi tiết đơn và stream ảnh từ MinIO -> `200 OK`.
6. `testCase06_RejectPrescriptionWithoutReason_FailsWith400`: Dược sĩ từ chối không có lý do -> `400 Bad Request`.
7. `testCase07_RejectPrescriptionWithReason_SucceedsWithTU_CHOI`: Dược sĩ từ chối kèm lý do hợp lệ -> `200 OK` (`TU_CHOI`).
8. `testCase08_ApprovePrescription_SucceedsWithDA_DUYET`: Dược sĩ duyệt đơn với danh mục thuốc -> `200 OK` (`DA_DUYET`).
9. `testCase09_ReApproveProcessedPrescription_FailsWithConflict`: Duyệt lại đơn đã xử lý -> `409 Conflict` (`PRESCRIPTION_ALREADY_PROCESSED`).
10. `testCase10_ValidatePrescriptionForOrder`: Validate đơn hết hạn -> `422`, vượt số lượng -> `422`, đơn hợp lệ -> `200 OK` (`valid: true`).
11. `testCase11_LinkPrescriptionToOrder`: Gắn đơn thuốc vào đơn hàng -> `201 Created`, gọi lại cùng orderId -> `200/201 OK` (Idempotent), gắn trùng sang orderId khác -> `409 Conflict` (`PRESCRIPTION_ALREADY_USED`).
