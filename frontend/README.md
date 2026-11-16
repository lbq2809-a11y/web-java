# Frontend — Hệ Thống Nhà Thuốc Trực Tuyến & Tư Vấn Dược AI

Ứng dụng giao diện người dùng (Lần 1) xây dựng bằng **React + Vite + TypeScript (Strict Mode)**, **Tailwind CSS**, **TanStack Query**, **Axios** và **React Router**, thiết kế theo phong cách **Soft Medical UI**.

---

## 1. Yêu cầu môi trường & Cài đặt

* **Node.js**: phiên bản 20+ (đã kiểm tra trên `v24.12.0`).
* **npm**: phiên bản 10+.

### Các bước cài đặt và chạy Dev Server:

```bash
cd frontend
npm install
npm run dev
```

Ứng dụng chạy tại mặc định: **`http://localhost:5173`**

### Kiểm tra TypeScript và đóng gói Production Build:

```bash
cd frontend
npm run build
```

---

## 2. Cấu hình Biến môi trường & Proxy

Tệp `.env` (đã tạo sẵn theo mẫu `.env.example`):

```env
VITE_API_BASE_URL=/api
```

### Giải thích cấu hình Vite Proxy (`vite.config.ts`)

Trong `vite.config.ts`, các lời gọi bắt đầu bằng `/api` được chuyển tiếp như sau:

1. **`/api/v1/consultations` $\rightarrow$ `http://localhost:8088` *(Cấu hình tạm thời)***:
   * Hiện tại `api-gateway` (cổng `8080`) chưa khai báo route chuyển tiếp tới `consultation-service` (`8088`). Do đó Vite dev server tạm thời proxy trực tiếp `/api/v1/consultations` sang cổng `8088` để tính năng Tư vấn AI hoạt động trơn tru mà không gặp lỗi CORS ở trình duyệt.
   * Sau này khi `api-gateway` bổ sung route cho `consultation-service`, chỉ cần gỡ rule `/api/v1/consultations` khỏi `vite.config.ts`.
2. **`/api` (các route còn lại như `auth`, `medicines`, `categories`) $\rightarrow$ `http://localhost:8080`**:
   * Đi tập trung qua `api-gateway` (`8080`), gateway sẽ định tuyến tới `auth-service` (`8082`) và `medicine-service` (`8081`).

---

## 3. Các Backend Services cần bật để chạy đầy đủ Lần 1

Trước khi chạy giao diện, đảm bảo hạ tầng Docker (`docker compose up -d`) và 4 microservice Quarkus sau đang hoạt động:

| Service | Cổng | Vai trò | Lệnh chạy |
| :--- | :---: | :--- | :--- |
| `api-gateway` | `8080` | Cổng điều phối API cho Auth & Medicine | `cd api-gateway && ./mvnw quarkus:dev` |
| `medicine-service` | `8081` | Danh mục thuốc (OTC/ETC) & chi tiết thuốc | `cd medicine-service && ./mvnw quarkus:dev` |
| `auth-service` | `8082` | Đăng ký, Đăng nhập JWT RS256, `/auth/me` | `cd auth-service && ./mvnw quarkus:dev` |
| `consultation-service` | `8088` | Trợ lý Tư vấn AI 2 tầng & lịch sử hội thoại | `cd consultation-service && ./mvnw quarkus:dev` |

---

## 4. Tài khoản thử nghiệm (Seed từ `auth-service`)

Mật khẩu chung cho toàn bộ tài khoản mẫu trong môi trường dev là **`123456`**:

| Vai trò | Email | Mật khẩu |
| :--- | :--- | :---: |
| Khách hàng (`KHACH_HANG`) | `khach_hang@pharmacy.local` | `123456` |
| Dược sĩ (`DUOC_SI`) | `duoc_si@pharmacy.local` | `123456` |
| Quản trị viên (`ADMIN`) | `admin@pharmacy.local` | `123456` |

*(Trang Đăng nhập cũng có sẵn nút điền nhanh tài khoản Khách hàng / Dược sĩ để thuận tiện kiểm thử).*
