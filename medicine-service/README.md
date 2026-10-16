# Medicine Service (Cổng 8081 - MongoDB `medicine_db`)

Microservice quản lý danh mục nhóm thuốc (`categories`), danh sách thuốc (`medicines` - phân loại **OTC** và **ETC**) và đánh giá sản phẩm (`reviews`) của hệ thống **Nhà thuốc trực tuyến**.

---

## 1. Công nghệ & Cấu hình

- **Framework**: Java 21 + Quarkus 3.39.4 (`quarkus-mongodb-panache`, `quarkus-rest-jackson`, `quarkus-smallrye-jwt`, `quarkus-smallrye-openapi`, `quarkus-hibernate-validator`, `quarkus-opentelemetry`).
- **Cơ sở dữ liệu**: MongoDB `localhost:27017` — Database: `medicine_db`.
- **Cổng HTTP**: `8081`.
- **Hợp đồng JWT**: Xem chi tiết tại [../docs/JWT-CONTRACT.md](../docs/JWT-CONTRACT.md). Service sử dụng khóa công khai `../keys/publicKey.pem` (thuật toán `RS256`) để xác thực chữ ký JWT.

---

## 2. Chuẩn bị cặp khóa RSA & Khởi chạy Service

### Bước 1: Sinh cặp khóa RSA 2048-bit (PKCS#8) dùng chung
Nếu trong thư mục `keys/` ở gốc dự án chưa có `privateKey.pem` và `publicKey.pem`, chạy lệnh sau tại thư mục gốc `d:\pharmacy-microservices`:
```powershell
powershell -ExecutionPolicy Bypass -File .\keys\generate-keys.ps1
```
*(File `keys/privateKey.pem` đã được đưa vào `.gitignore` để đảm bảo an toàn).*

### Bước 2: Đảm bảo MongoDB đang chạy
```powershell
docker compose up -d mongodb
```

### Bước 3: Chạy `medicine-service` ở chế độ Dev
```powershell
cd medicine-service
.\mvnw.cmd quarkus:dev
```
Khi khởi động lần đầu và `medicine_db` còn trống, `DataInitializer` sẽ tự động tạo đầy đủ các MongoDB Indexes và nạp **6 danh mục + 30 thuốc DEMO** (18 thuốc OTC và 12 thuốc kê đơn ETC).

---

## 3. Hướng dẫn tạo Token thử & Xác thực trên Swagger UI (khi chưa có `auth-service`)

Giao diện **Swagger UI** của `medicine-service` nằm tại:
- **URL**: http://localhost:8081/q/swagger-ui

Ở chế độ `dev` (`quarkus:dev`), service bật sẵn endpoint tiện ích `/api/v1/dev/tokens` (`@IfBuildProfile(anyOf = {"dev", "test"})`) để bạn lấy nhanh JWT cho từng vai trò:

### Bước 1: Lấy JWT Token cho 1 trong 3 vai trò (`KHACH_HANG`, `DUOC_SI`, `ADMIN`)
Bạn có thể bấm trực tiếp vào nhóm **`Dev Token Generator (Dev/Test Only)`** trên Swagger UI (`GET /api/v1/dev/tokens`) hoặc chạy lệnh PowerShell:

- **Lấy token vai trò `ADMIN`** (toàn quyền thêm/sửa/xóa thuốc & danh mục):
  ```powershell
  (Invoke-RestMethod "http://localhost:8081/api/v1/dev/tokens?role=ADMIN").token
  ```
- **Lấy token vai trò `DUOC_SI`** (được thêm/sửa thuốc & danh mục, không được xóa):
  ```powershell
  (Invoke-RestMethod "http://localhost:8081/api/v1/dev/tokens?role=DUOC_SI").token
  ```
- **Lấy token vai trò `KHACH_HANG`** (chỉ xem được thuốc đang hoạt động và gửi đánh giá `POST /api/v1/medicines/{id}/reviews`):
  ```powershell
  (Invoke-RestMethod "http://localhost:8081/api/v1/dev/tokens?role=KHACH_HANG").token
  ```

### Bước 2: Dán Token vào nút Authorize trên Swagger UI
1. Mở http://localhost:8081/q/swagger-ui.
2. Nhấn nút **`Authorize`** (biểu tượng ổ khóa ở góc trên bên phải).
3. Tại ô **`Value`** của mục `bearerAuth (http, Bearer)`, **dán chuỗi token** vừa copy ở Bước 1 (không cần gõ chữ `Bearer ` ở đầu).
4. Nhấn **`Authorize`** -> **`Close`**. Từ lúc này mọi lời gọi thử nghiệm trên Swagger UI sẽ tự động đính kèm header `Authorization: Bearer <token>`.

---

## 4. Danh sách API chính

- **Danh mục (`/api/v1/categories`)**:
  - `GET /api/v1/categories`: Danh sách danh mục (`?includeInactive=true` cho `ADMIN`/`DUOC_SI`).
  - `GET /api/v1/categories/{id}`: Chi tiết danh mục.
  - `POST /api/v1/categories` (`ADMIN`, `DUOC_SI`): Tạo danh mục mới.
  - `PUT /api/v1/categories/{id}` (`ADMIN`, `DUOC_SI`): Cập nhật danh mục (tự động đồng bộ `categoryName` sang các thuốc thuộc danh mục).
  - `DELETE /api/v1/categories/{id}` (`ADMIN`): Ẩn danh mục (chặn nếu còn thuốc đang hoạt động).
- **Thuốc (`/api/v1/medicines`)**:
  - `GET /api/v1/medicines`: Tìm kiếm (`keyword`), lọc (`categoryId`, `prescriptionType=OTC|ETC`, `minPrice`, `maxPrice`), sắp xếp (`sortBy=name|price|averageRating|createdAt`, `sortDir=asc|desc`), phân trang (`page`, `size`).
  - `GET /api/v1/medicines/batch?ids=id1,id2,...`: Lấy thông tin nhiều thuốc cùng lúc (tối đa 50 ID) phục vụ `order-service`.
  - `GET /api/v1/medicines/{id}`: Xem chi tiết thuốc.
  - `POST /api/v1/medicines` (`ADMIN`, `DUOC_SI`): Thêm thuốc mới.
  - `PUT /api/v1/medicines/{id}` (`ADMIN`, `DUOC_SI`): Cập nhật thuốc.
  - `DELETE /api/v1/medicines/{id}` (`ADMIN`): Xóa mềm (`active = false`).
- **Đánh giá (`/api/v1/medicines/{medicineId}/reviews`)**:
  - `GET /api/v1/medicines/{medicineId}/reviews`: Danh sách đánh giá của thuốc (phân trang).
  - `POST /api/v1/medicines/{medicineId}/reviews` (`KHACH_HANG`, `DUOC_SI`, `ADMIN`): Tạo đánh giá (mỗi `userId` tối đa 1 đánh giá cho mỗi thuốc; điểm trung bình `averageRating` và `reviewCount` được tính lại bằng MongoDB Aggregation).

---

## 5. Chạy Kiểm thử Tích hợp (Testcontainers)

```powershell
cd medicine-service
.\mvnw.cmd test
```
