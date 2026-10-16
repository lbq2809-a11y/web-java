# Hợp đồng JWT Dùng Chung Toàn Hệ Thống (JWT Contract)

Tài liệu này quy định cấu trúc **JSON Web Token (JWT)** thống nhất giữa **`auth-service`** (nơi phát hành và ký token) và tất cả các microservices khác (`medicine-service`, `prescription-service`, `order-service`, `payment-service`, `notification-service`, `inventory-service`, `consultation-service`, `recommendation-service`, `api-gateway`).

---

## 1. Thuật toán ký và Quản lý khóa (RSA-2048)

- **Thuật toán ký (`alg`)**: `RS256` (RSA Signature with SHA-256).
- **Định dạng khóa**:
  - **Private Key (`keys/privateKey.pem`)**: RSA 2048-bit, định dạng **PKCS#8** (`-----BEGIN PRIVATE KEY-----`). Chỉ dùng bởi `auth-service` (và tiện ích dev tạo token thử) để ký JWT. **Tuyệt đối không commit lên Git** (đã khai báo trong `.gitignore`).
  - **Public Key (`keys/publicKey.pem`)**: RSA 2048-bit, định dạng **X.509 SubjectPublicKeyInfo** (`-----BEGIN PUBLIC KEY-----`). Dùng bởi tất cả các service để xác thực chữ ký JWT.
- **Cách sinh cặp khóa cho môi trường phát triển**:
  Tại thư mục gốc `d:\pharmacy-microservices`, chạy script:
  ```powershell
  .\keys\generate-keys.ps1
  ```

---

## 2. Các Claims chuẩn trong JWT Payload

Mọi Access Token do hệ thống phát hành bắt buộc tuân thủ các claims chuẩn của **MicroProfile JWT (`org.eclipse.microprofile.jwt.JsonWebToken`)**:

| Claim | Kiểu dữ liệu | Bắt buộc | Ý nghĩa & Quy ước | Ví dụ |
| :--- | :--- | :---: | :--- | :--- |
| `iss` | `String` | Có | Định danh đơn vị phát hành token (cố định toàn hệ thống) | `"https://pharmacy.com/issuer"` |
| `sub` | `String` (UUID) | Có | `userId` của người dùng (khóa chính UUID trong `auth_db.users`) | `"550e8400-e29b-41d4-a716-446655440000"` |
| `upn` | `String` | Có | Định danh chính của người dùng (trùng với `email` để tương thích chuẩn MP-JWT) | `"khachhang@pharmacy.local"` |
| `email` | `String` | Có | Địa chỉ email của người dùng | `"khachhang@pharmacy.local"` |
| `name` | `String` | Có | Họ và tên hiển thị của người dùng | `"Nguyễn Văn A"` |
| `groups` | `Array<String>` | Có | Danh sách vai trò để phân quyền với `@RolesAllowed`. Chỉ nhận các giá trị: `KHACH_HANG`, `DUOC_SI`, `ADMIN` | `["KHACH_HANG"]` |
| `iat` | `NumericDate` | Có | Thời điểm phát hành token (epoch seconds) | `1760600000` |
| `exp` | `NumericDate` | Có | Thời điểm hết hạn token (Access Token mặc định: **3600 giây = 1 giờ**; Refresh Token: **7 ngày**) | `1760603600` |
| `jti` | `String` (UUID) | Có | Mã định danh duy nhất của token | `"8f9d2a1b-3c4e-4f5a-9b8c-7d6e5f4a3b2c"` |

### Ví dụ Payload giải mã:
```json
{
  "iss": "https://pharmacy.com/issuer",
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "upn": "duocsi@pharmacy.local",
  "email": "duocsi@pharmacy.local",
  "name": "DS. Trần Thị B",
  "groups": [
    "DUOC_SI"
  ],
  "iat": 1760600000,
  "exp": 1760603600,
  "jti": "8f9d2a1b-3c4e-4f5a-9b8c-7d6e5f4a3b2c"
}
```

---

## 3. Cấu hình chuẩn trong `application.properties`

### 3.1. Tại các service xác thực token (`medicine-service`, `prescription-service`, `order-service`, v.v.)
```properties
mp.jwt.verify.issuer=${JWT_ISSUER:https://pharmacy.com/issuer}
mp.jwt.verify.publickey.location=${JWT_PUBLIC_KEY_LOCATION:../keys/publicKey.pem}
mp.jwt.verify.publickey.algorithm=RS256
```

### 3.2. Tại `auth-service` (và môi trường dev sinh token thử)
```properties
mp.jwt.verify.issuer=${JWT_ISSUER:https://pharmacy.com/issuer}
mp.jwt.verify.publickey.location=${JWT_PUBLIC_KEY_LOCATION:../keys/publicKey.pem}
mp.jwt.verify.publickey.algorithm=RS256
smallrye.jwt.sign.key.location=${JWT_PRIVATE_KEY_LOCATION:../keys/privateKey.pem}
```

