# Auth Service (Cổng 8082 - PostgreSQL `auth_db` + SmallRye JWT)

Microservice quản trị tài khoản người dùng, xác thực đăng ký, đăng nhập và cấp phát **JSON Web Token (JWT)** được ký bằng thuật toán RSA-2048 (khóa bí mật `keys/privateKey.pem`). Token phát hành tuân thủ hoàn toàn hợp đồng hệ thống tại [`docs/JWT-CONTRACT.md`](../docs/JWT-CONTRACT.md) và được xác thực dùng chung bởi tất cả các service khác (`medicine-service`, `prescription-service`, `order-service`, v.v.).

---

## 1. Công nghệ & Cấu hình

- **Framework**: Java 21 + Quarkus 3.39.4 (`quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`, `quarkus-flyway`, `quarkus-smallrye-jwt`, `quarkus-smallrye-jwt-build`, `quarkus-rest-jackson`, `quarkus-hibernate-validator`, `quarkus-smallrye-openapi`).
- **Cơ sở dữ liệu**: PostgreSQL `localhost:5432` — Database: `auth_db` (user: `auth_user`, pass: `auth_pass`).
- **Mật khẩu**: Băm bảo mật bằng thuật toán **BCrypt** (10 vòng lặp) qua thư viện `jbcrypt`.
- **Ký JWT Token**: Sử dụng **Quarkus SmallRye JWT Build** với khóa RSA 2048-bit định dạng PKCS#8 từ file `../keys/privateKey.pem`.
- **Xác thực JWT nội bộ**: Public key `../keys/publicKey.pem`, thuật toán `RS256`, đơn vị phát hành `https://pharmacy.com/issuer`.
- **Cổng HTTP**: `8082`.

---

## 2. Tài Khoản Seed Mặc Định (Mật khẩu: `123456`)

Hệ thống được khởi tạo sẵn 3 tài khoản mặc định phục vụ kiểm thử và liên kết giữa các microservices:

| Vai trò (`role`) | UUID (`sub`) | Email | Mật khẩu | Họ và tên |
| :---: | :--- | :--- | :---: | :--- |
| **`KHACH_HANG`** | `11111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa` | `khach_hang@pharmacy.local` | `123456` | Nguyễn Văn A |
| **`DUOC_SI`** | `22222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb` | `duoc_si@pharmacy.local` | `123456` | DS. Trần Thị Dược |
| **`ADMIN`** | `00000000-0000-0000-0000-000000000000` | `admin@pharmacy.local` | `123456` | Quản trị viên Admin |

---

## 3. Danh Sách REST APIs

| Method | Endpoint | Quyền hạn (`Role`) | Mô tả |
| :--- | :--- | :---: | :--- |
| `POST` | `/api/v1/auth/register` | `@PermitAll` | Đăng ký tài khoản khách hàng mới, tự sinh UUID, băm BCrypt, trả về JWT token |
| `POST` | `/api/v1/auth/login` | `@PermitAll` | Đăng nhập bằng email + mật khẩu, trả về JWT access token thời hạn 24 giờ |
| `GET` | `/api/v1/auth/me` | `@RolesAllowed({"KHACH_HANG", "DUOC_SI", "ADMIN"})` | Lấy thông tin hồ sơ tài khoản từ JWT token (Bearer) |

---

## 4. Hướng dẫn Khởi Chạy Service

### Bước 1: Khởi động hạ tầng Docker
Từ thư mục gốc `d:\pharmacy-microservices`:
```powershell
docker compose up -d postgres
```

### Bước 2: Chạy `auth-service` ở chế độ Dev
```powershell
cd auth-service
.\mvnw.cmd quarkus:dev
```
Quarkus sẽ tự động:
1. Chạy Flyway migration `V1__init_users.sql` tạo bảng `users` và seed 3 tài khoản mặc định vào PostgreSQL `auth_db`.
2. Khởi chạy giao diện Swagger UI tại: [http://localhost:8082/q/swagger-ui](http://localhost:8082/q/swagger-ui).

---

## 5. Chạy Kiểm Thử Tự Động (Integration Tests)

Chạy lệnh sau để thực thi toàn bộ **10 ca kiểm thử tích hợp**:
```powershell
cd auth-service
.\mvnw.cmd test
```

### Bộ 10 ca kiểm thử bao phủ:
1. `testCase01_RegisterNewCustomer_Success`: Đăng ký tài khoản mới thành công $\to$ `201 Created`, nhận được JWT hợp lệ và thông tin user.
2. `testCase02_RegisterDuplicateEmail_FailsWith400`: Đăng ký lại email đã tồn tại $\to$ Bị từ chối `400 Bad Request` (`EMAIL_ALREADY_EXISTS`).
3. `testCase03_RegisterInvalidData_FailsWith400`: Đăng ký với email sai cú pháp hoặc mật khẩu < 6 ký tự $\to$ `400 Bad Request` (`VALIDATION_ERROR`).
4. `testCase04_LoginSuccess_DefaultCustomer`: Đăng nhập tài khoản khách hàng mặc định (`khach_hang@pharmacy.local` / `123456`) $\to$ `200 OK`, `role = KHACH_HANG`.
5. `testCase05_LoginSuccess_DefaultPharmacist`: Đăng nhập tài khoản dược sĩ mặc định (`duoc_si@pharmacy.local` / `123456`) $\to$ `200 OK`, `role = DUOC_SI`.
6. `testCase06_LoginSuccess_DefaultAdmin`: Đăng nhập tài khoản admin mặc định (`admin@pharmacy.local` / `123456`) $\to$ `200 OK`, `role = ADMIN`.
7. `testCase07_LoginWrongPassword_FailsWith401`: Đăng nhập đúng email nhưng sai mật khẩu $\to$ Bị từ chối `401 Unauthorized` (`INVALID_CREDENTIALS`).
8. `testCase08_LoginNonExistentEmail_FailsWith401`: Đăng nhập email không tồn tại $\to$ Bị từ chối `401 Unauthorized` (`INVALID_CREDENTIALS`).
9. `testCase09_GetMeWithValidToken_Success`: Gọi `/api/v1/auth/me` bằng token vừa đăng nhập $\to$ `200 OK`, trả về đúng thông tin hồ sơ.
10. `testCase10_GetMeWithoutToken_FailsWith401`: Gọi `/api/v1/auth/me` không có header `Authorization` $\to$ Bị chặn `401 Unauthorized`.
