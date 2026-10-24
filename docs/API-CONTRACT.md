# Hợp Đồng Giao Tiếp Nội Bộ Giữa Các Microservices (API & Kafka Contract)

Tài liệu này quy định chuẩn giao tiếp đồng bộ (**REST Client**) và bất đồng bộ (**Apache Kafka**) giữa các microservices trong hệ thống **Nhà thuốc trực tuyến**, đảm bảo nguyên tắc **mỗi service một CSDL riêng, không đọc trực tiếp DB của nhau**.

---

## 1. Quy Ước Chung Toàn Hệ Thống

### 1.1. Kiểu định danh (ID) giữa MongoDB và PostgreSQL
- **`medicineId` (và `categoryId`, `reviewId`, `notificationId`)**: Kiểu **`String`** biểu diễn chuỗi Hex 24 ký tự của **MongoDB `ObjectId`** (ví dụ: `"6ad23be6c034632667baaa05"`). Các bảng trong PostgreSQL (`prescription_items`, `order_items`) khi tham chiếu tới `medicine_id` bắt buộc lưu kiểu `VARCHAR(24)` (hoặc `VARCHAR(50)`), **không dùng kiểu `UUID` cho `medicine_id`**.
- **`userId`, `prescriptionId`, `orderId`, `paymentId`, `inventoryId`**: Kiểu **`UUID`** chuẩn 36 ký tự của **PostgreSQL** (ví dụ: `"550e8400-e29b-41d4-a716-446655440000"`).
- **Tiền tệ (`price`, `totalAmount`, `amount`)**: Trong PostgreSQL lưu `NUMERIC(19,2)`, trong MongoDB lưu `Decimal128`, trong JSON truyền dạng số thập phân (`BigDecimal`, ví dụ: `285000.00`).

### 1.2. Các HTTP Headers chuẩn khi gọi liên service
| Header | Bắt buộc | Mô tả & Quy ước |
| :--- | :---: | :--- |
| `Authorization` | Tùy API | Định dạng `Bearer <JWT>`. Khi một service (ví dụ `api-gateway`, `order-service`, `payment-service`) gọi sang service khác trong ngữ cảnh thao tác của người dùng, phải **chuyển tiếp (propagate) nguyên vẹn header `Authorization`** để service đích xác thực JWT và kiểm tra quyền sở hữu. |
| `X-Correlation-Id` | Khuyến nghị | Chuỗi định danh vết yêu cầu (UUID). Được sinh tại `api-gateway` (nếu client chưa gửi) và chuyển tiếp qua mọi REST call cũng như đính kèm vào Kafka event (`correlationId`) để truy vết tập trung cùng OpenTelemetry/Jaeger. |
| `Idempotency-Key` | Bắt buộc khi đặt hàng | Chuỗi UUID do client sinh khi gọi `POST /api/v1/orders` (checkout) để chống đặt trùng đơn hàng khi mạng chập chờn hoặc người dùng bấm đúp. |

### 1.3. Định dạng phản hồi lỗi thống nhất
Mọi lỗi HTTP (`400`, `401`, `403`, `404`, `409`, `422`, `429`, `500`, `503`) từ tất cả các service đều trả về cấu trúc JSON thống nhất:
```json
{
  "code": "ERROR_CODE_UPPER_SNAKE_CASE",
  "message": "Thông báo lỗi tiếng Việt rõ ràng",
  "details": [
    "Chi tiết lỗi cụ thể thứ nhất (nếu có)"
  ]
}
```

### 1.4. Tên trạng thái chuẩn
- **3 vai trò người dùng (`Role`)**: `KHACH_HANG`, `DUOC_SI`, `ADMIN`.
- **Phân loại thuốc (`PrescriptionType`)**:
  - `OTC`: Thuốc không kê đơn (mua tự do).
  - `ETC`: Thuốc kê đơn (chỉ được mua khi có đơn thuốc đã được dược sĩ duyệt).
- **Trạng thái đơn thuốc (`PrescriptionStatus`)**: `CHO_DUYET`, `DA_DUYET`, `TU_CHOI`.
- **Trạng thái đơn hàng (`OrderStatus`)**: `CHO_XU_LY`, `DANG_GIAO`, `HOAN_THANH`, `DA_HUY`, `HOAN_TRA`.
- **Phương thức thanh toán (`PaymentMethod`)**: `COD`, `VNPAY`, `MOMO`, `QR`.

---

## 2. Hợp Đồng REST API Nội Bộ Giữa Các Service

### 2.1. `medicine-service` (`http://localhost:8081`)
*(Đã triển khai thực tế trong `MedicineResource.java` & `MedicineService.java`)*

#### (A) Lấy thông tin chi tiết 1 thuốc
- **Service gọi**: `prescription-service` (khi dược sĩ duyệt thuốc kê đơn), `order-service` (khi thêm thuốc vào giỏ hàng Redis), `recommendation-service`.
- **Method & Path**: `GET /api/v1/medicines/{id}`
- **Path Parameter**: `id` (`String` - 24 ký tự hex ObjectId của MongoDB).
- **Headers**: `Authorization: Bearer <JWT>` *(tùy chọn; khách thường hoặc không có token chỉ xem được thuốc có `active = true`)*.
- **Response thành công (`200 OK` - `MedicineResponse`)**:
  ```json
  {
    "id": "6ad23be6c034632667baaa05",
    "code": "MED-ETC-003",
    "name": "Augmentin 1g",
    "activeIngredient": "Amoxicillin 875mg, Clavulanic acid 125mg",
    "manufacturer": "GSK",
    "unit": "Hộp 2 vỉ x 7 viên",
    "price": 285000.00,
    "imageUrl": "https://placehold.co/400x400?text=MED-ETC-003",
    "indications": "[Dữ liệu demo - Thuốc kê đơn] Điều trị nhiễm khuẩn...",
    "contraindications": "Tiền sử dị ứng với kháng sinh nhóm Beta-lactam...",
    "dosage": "Uống vào đầu bữa ăn theo đúng liều lượng...",
    "prescriptionType": "ETC",
    "categoryId": "6ad23be6c034632667baaa01",
    "categoryName": "Kháng sinh - Kháng khuẩn",
    "active": true,
    "averageRating": 5.0,
    "reviewCount": 1,
    "createdAt": "2026-10-16T14:00:00Z",
    "updatedAt": "2026-10-16T14:00:00Z"
  }
  ```
- **Mã lỗi**:
  - `400 Bad Request` (`INVALID_MEDICINE_ID`): `id` không đúng định dạng ObjectId 24 ký tự hex.
  - `404 Not Found` (`MEDICINE_NOT_FOUND`): Không tìm thấy thuốc hoặc thuốc đã ngừng kinh doanh (`active = false`).

#### (B) Lấy thông tin nhiều thuốc cùng lúc theo lô (Batch)
- **Service gọi**: `order-service` (khi xem giỏ hàng và khi checkout để lấy snapshot tên thuốc, giá bán mới nhất và kiểm tra thuốc nào là `ETC`), `prescription-service`.
- **Method & Path**: `GET /api/v1/medicines/batch?ids={id1},{id2},...`
- **Query Parameter**: `ids` (chuỗi các `medicineId` phân tách bằng dấu phẩy, **tối đa 50 ID**).
- **Response thành công (`200 OK` - `List<MedicineResponse>`)**:
  - Trả về mảng JSON các đối tượng `MedicineResponse` giữ đúng thứ tự ID yêu cầu (những ID không tồn tại trong DB sẽ không có trong mảng kết quả; `order-service` so sánh kích thước/tập ID trả về hoặc kiểm tra cờ `active == true` để báo lỗi nếu thuốc không tồn tại hoặc đã ẩn).
- **Mã lỗi**:
  - `400 Bad Request` (`EMPTY_BATCH_IDS`): Tham số `ids` trống hoặc rỗng.
  - `400 Bad Request` (`BATCH_SIZE_EXCEEDED`): Số lượng ID vượt quá 50.
  - `400 Bad Request` (`INVALID_MEDICINE_ID`): Có ID không đúng định dạng ObjectId 24 ký tự hex (`details` liệt kê danh sách các ID sai định dạng).

---

### 2.2. `prescription-service` (`http://localhost:8083`)
*(Thiết kế theo `docs/B3-prescription-service.md` & `docs/B4-order-service.md` phục vụ `order-service` khi đặt đơn có thuốc `ETC`)*

#### (A) Kiểm tra đơn thuốc có hợp lệ cho danh sách thuốc ETC và số lượng cần mua không
- **Service gọi**: `order-service` (ở bước Checkout khi trong giỏ hàng có ít nhất 1 thuốc `prescriptionType == "ETC"`).
- **Method & Path**: `POST /api/v1/prescriptions/{prescriptionId}/validate-for-order`
- **Headers**:
  - `Authorization: Bearer <JWT>` (chuyển tiếp từ khách hàng đang đặt đơn)
  - `X-Correlation-Id: <UUID>`
- **Request Body**:
  ```json
  {
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [
      {
        "medicineId": "6ad23be6c034632667baaa05",
        "quantity": 2
      }
    ]
  }
  ```
- **Quy tắc kiểm tra tại `prescription-service`**:
  1. Đơn thuốc `prescriptionId` (`UUID`) tồn tại và thuộc về đúng `customerId` (`UUID`).
  2. Trạng thái đơn thuốc là `DA_DUYET` và chưa hết hạn (`expiresAt > now`).
  3. Mọi `medicineId` (`ObjectId` string) trong request đều nằm trong danh sách `prescription_items` đã được dược sĩ duyệt và `quantity` yêu cầu không vượt quá số lượng tối đa cho phép (`maxAllowedQuantity` hoặc số lượng còn lại của đơn thuốc).
- **Response thành công (`200 OK`)**:
  ```json
  {
    "prescriptionId": "770e8400-e29b-41d4-a716-446655440111",
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "valid": true,
    "status": "DA_DUYET",
    "expiresAt": "2026-10-30T23:59:59Z",
    "violations": []
  }
  ```
- **Mã lỗi**:
  - `404 Not Found` (`PRESCRIPTION_NOT_FOUND`): Không tìm thấy đơn thuốc.
  - `403 Forbidden` (`PRESCRIPTION_OWNER_MISMATCH`): Đơn thuốc không thuộc về `customerId` đang đặt hàng.
  - `422 Unprocessable Entity` (hoặc `400 Bad Request` với mã `PRESCRIPTION_INVALID`): Đơn thuốc chưa được duyệt (`CHO_DUYET`), bị từ chối (`TU_CHOI`), đã hết hạn (`PRESCRIPTION_EXPIRED`), hoặc thuốc/số lượng không khớp (`details` trả danh sách cụ thể thuốc nào thiếu đơn hoặc vượt số lượng cho phép).

#### (B) Gắn (liên kết) đơn thuốc vào đơn hàng sau khi tạo đơn thành công
- **Service gọi**: `order-service` (ngay sau khi lưu `orders` thành công trong bước Checkout).
- **Method & Path**: `POST /api/v1/prescriptions/{prescriptionId}/order-links`
- **Headers**:
  - `Authorization: Bearer <JWT>`
  - `X-Correlation-Id: <UUID>`
- **Request Body**:
  ```json
  {
    "orderId": "990e8400-e29b-41d4-a716-446655440222",
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [
      {
        "medicineId": "6ad23be6c034632667baaa05",
        "quantity": 2
      }
    ]
  }
  ```
- **Response thành công (`201 Created` hoặc `200 OK` nếu gọi lại idempotent cùng `orderId`)**:
  ```json
  {
    "id": "aa0e8400-e29b-41d4-a716-446655440333",
    "prescriptionId": "770e8400-e29b-41d4-a716-446655440111",
    "orderId": "990e8400-e29b-41d4-a716-446655440222",
    "linkedAt": "2026-10-17T10:15:30Z"
  }
  ```
- **Mã lỗi**:
  - `404 Not Found` (`PRESCRIPTION_NOT_FOUND`): Không tìm thấy đơn thuốc.
  - `403 Forbidden` (`PRESCRIPTION_OWNER_MISMATCH`): Không đúng chủ đơn thuốc.
  - `409 Conflict` (`PRESCRIPTION_ALREADY_USED`): Đơn thuốc chỉ dùng 1 lần đã được gắn với một `orderId` khác trước đó.

---

### 2.3. `order-service` (`http://localhost:8084`)
*(Thiết kế theo `docs/B4-order-service.md` & `docs/B5-payment-service.md` phục vụ `payment-service` kiểm tra đơn hàng trước khi tạo thanh toán)*

#### Lấy thông tin đơn hàng để khởi tạo / đối chiếu thanh toán
- **Service gọi**: `payment-service` (khi tạo giao dịch thanh toán COD / VNPay / QR để lấy chính xác `totalAmount` từ `order-service`, không tin tưởng số tiền do client tự gửi lên).
- **Method & Path**: `GET /api/v1/orders/{orderId}`
- **Path Parameter**: `orderId` (`UUID`).
- **Headers**:
  - `Authorization: Bearer <JWT>` (chuyển tiếp JWT của khách hàng hoặc `ADMIN`/`DUOC_SI`)
  - `X-Correlation-Id: <UUID>`
- **Response thành công (`200 OK` - `OrderResponse`)**:
  ```json
  {
    "id": "990e8400-e29b-41d4-a716-446655440222",
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "prescriptionId": "770e8400-e29b-41d4-a716-446655440111",
    "status": "CHO_XU_LY",
    "totalAmount": 570000.00,
    "shippingAddress": {
      "recipientName": "Nguyễn Văn A",
      "phone": "0901234567",
      "addressLine": "123 Nguyễn Văn Cừ, Quận 5, TP.HCM"
    },
    "items": [
      {
        "medicineId": "6ad23be6c034632667baaa05",
        "medicineCode": "MED-ETC-003",
        "medicineName": "Augmentin 1g",
        "unit": "Hộp 2 vỉ x 7 viên",
        "prescriptionType": "ETC",
        "unitPrice": 285000.00,
        "quantity": 2,
        "subtotal": 570000.00
      }
    ],
    "createdAt": "2026-10-17T10:15:00Z",
    "updatedAt": "2026-10-17T10:15:00Z"
  }
  ```
- **Mã lỗi**:
  - `401 Unauthorized` (`UNAUTHORIZED`): Thiếu hoặc sai JWT.
  - `403 Forbidden` (`FORBIDDEN`): Khách hàng cố xem đơn hàng của người khác.
  - `404 Not Found` (`ORDER_NOT_FOUND`): Không tìm thấy đơn hàng với `orderId` tương ứng.

---

### 2.4. `auth-service` (Đăng nhập bằng mã QR - `http://localhost:8082`)
*(Giai đoạn 3 theo `docs/B2-auth-service.md` & `docs/JWT-CONTRACT.md`)*

#### (A) Tạo phiên đăng nhập QR mới
- **Caller**: Trình duyệt Web / Client (qua `api-gateway` hoặc trực tiếp `auth-service`).
- **Method & Path**: `POST /api/v1/auth/qr/session`
- **Headers**:
  - `X-Forwarded-For`: `<Client-IP>` *(tùy chọn; dùng để giới hạn tần suất 10 phiên/phút theo IP)*.
- **Request Body**: Trống.
- **Response thành công (`201 Created` - `CreateQrSessionResponse`)**:
  ```json
  {
    "sessionId": "4b684cb3-074d-4074-b593-9c8da4c424a7",
    "pollSecret": "a8f3b194d2e7c4f68153b92c019d45e7",
    "qrContent": "4b684cb3-074d-4074-b593-9c8da4c424a7",
    "status": "PENDING",
    "expiresAt": "2026-10-23T14:15:00Z"
  }
  ```
  *(Ghi chú: `qrContent` chỉ chứa `sessionId` để hiển thị mã QR trên Web, tuyệt đối không chứa `pollSecret`)*.
- **Mã lỗi**:
  - `429 Too Many Requests` (`TOO_MANY_REQUESTS`): Vượt quá giới hạn tạo phiên 10 phiên/phút từ cùng một IP.

#### (B) Kiểm tra trạng thái phiên QR (HTTP Polling)
- **Caller**: Trình duyệt Web / Client.
- **Method & Path**: `GET /api/v1/auth/qr/session/{sessionId}`
- **Path Parameter**: `sessionId` (`String` UUID).
- **Headers**:
  - `X-QR-Secret`: `<pollSecret>` *(Bắt buộc; phải khớp chính xác với `pollSecret` khi tạo phiên)*.
- **Response thành công khi phiên đang chờ (`200 OK`)**:
  ```json
  {
    "sessionId": "4b684cb3-074d-4074-b593-9c8da4c424a7",
    "status": "PENDING"
  }
  ```
- **Response thành công khi phiên đã được xác nhận (`200 OK` - `QrSessionStatusResponse`)**:
  ```json
  {
    "sessionId": "4b684cb3-074d-4074-b593-9c8da4c424a7",
    "status": "CONFIRMED",
    "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": {
      "id": "11111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
      "email": "khach_hang@pharmacy.local",
      "fullName": "Nguyễn Văn Khách",
      "phone": "0901234567",
      "role": "KHACH_HANG",
      "active": true,
      "createdAt": "2026-10-15T08:00:00Z"
    }
  }
  ```
  *(Ghi chú: Token chỉ trả một lần duy nhất; ngay sau khi trả token, phiên sẽ bị xóa khỏi Redis. Lần gọi tiếp theo sẽ trả về `404 QR_SESSION_NOT_FOUND`)*.
- **Mã lỗi**:
  - `403 Forbidden` (`FORBIDDEN`): Thiếu header `X-QR-Secret` hoặc sai `pollSecret`.
  - `404 Not Found` (`QR_SESSION_NOT_FOUND`): Phiên QR không tồn tại hoặc đã hết hạn TTL.

#### (C) Thiết bị di động xác nhận phiên đăng nhập QR
- **Caller**: Ứng dụng di động (sau khi người dùng đã đăng nhập và quét mã QR).
- **Method & Path**: `POST /api/v1/auth/qr/confirm`
- **Headers**:
  - `Authorization`: `Bearer <JWT>` *(Bắt buộc; danh tính người dùng lấy từ claim `sub` của JWT)*.
- **Request Body (`ConfirmQrRequest`)**:
  ```json
  {
    "sessionId": "4b684cb3-074d-4074-b593-9c8da4c424a7"
  }
  ```
- **Response thành công (`200 OK` - `ConfirmQrResponse`)**:
  ```json
  {
    "status": "CONFIRMED"
  }
  ```
- **Mã lỗi**:
  - `400 Bad Request` (`BAD_REQUEST`): Thiếu hoặc sai định dạng `sessionId`.
  - `401 Unauthorized` (`UNAUTHORIZED`): Thiếu hoặc sai JWT Bearer.
  - `404 Not Found` (`QR_SESSION_NOT_FOUND`): Phiên QR không tồn tại hoặc đã hết hạn TTL.
  - `409 Conflict` (`QR_SESSION_ALREADY_CONFIRMED`): Phiên QR đã được thiết bị khác xác nhận trước đó.

#### (D) Kênh thời gian thực WebSocket (Nhận kết quả trực tiếp)
- **URL**: `ws://localhost:8082/ws/qr/{sessionId}?secret=<pollSecret>`
- **Ghi chú**: Trình duyệt kết nối trực tiếp tới `auth-service` (cổng `8082`), không qua API Gateway.
- **Bắt tay**: Sai hoặc thiếu tham số `secret` sẽ bị từ chối kết nối ngay lập tức (mã đóng `1008 Policy Violation`).
- **Sự kiện đẩy khi xác nhận thành công**:
  ```json
  {
    "status": "CONFIRMED",
    "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": {
      "id": "11111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
      "email": "khach_hang@pharmacy.local",
      "fullName": "Nguyễn Văn Khách",
      "phone": "0901234567",
      "role": "KHACH_HANG",
      "active": true,
      "createdAt": "2026-10-15T08:00:00Z"
    }
  }
  ```
  *(Sau khi gửi message, phiên bị xóa khỏi Redis và kết nối WebSocket tự động đóng)*.
- **Sự kiện đẩy khi phiên hết hạn**:
  ```json
  {
    "status": "EXPIRED"
  }
  ```
  *(Sau khi gửi message, kết nối WebSocket tự động đóng)*.

---

### 2.5. `recommendation-service` (`http://localhost:8089`)
*(Thiết kế theo `docs/B10-recommendation-service.md`, sử dụng MongoDB `recommendation_db` để gợi ý thuốc theo thống kê mua hàng và an toàn dược phẩm)*

#### (A) Gợi ý thuốc thường được mua kèm (Frequently Bought Together)
- **Path**: `GET /api/v1/recommendations/also-bought/{medicineId}?limit=5`
- **Quyền truy cập**: Public (không yêu cầu JWT).
- **Query Parameter**: `limit` (mặc định: `5`, min: `1`, max: `20`).
- **Nghiệp vụ**:
  - Truy vấn ma trận đồng xuất hiện (`product_cooccurrence`), sắp xếp theo tần suất mua kèm giảm dần (`count DESC`), tie-break theo `coMedicineId ASC`.
  - Tra cứu chi tiết thuốc qua `medicine-service` (`GET /api/v1/medicines/batch?ids=...`).
  - Lọc bỏ thuốc ngừng bán (`active = false`). Thuốc kê đơn (`ETC`) vẫn được phép hiển thị với `requiresPrescription = true`.
- **Response thành công (`200 OK`)**:
  ```json
  [
    {
      "medicineId": "6ad23be6c034632667baaa02",
      "name": "Panadol Extra Đỏ",
      "price": 55000.00,
      "type": "OTC",
      "requiresPrescription": false,
      "reason": "FREQUENTLY_BOUGHT_TOGETHER"
    }
  ]
  ```

#### (B) Gợi ý cá nhân hóa cho người dùng (Personalized For Me)
- **Path**: `GET /api/v1/recommendations/for-me?limit=5`
- **Quyền truy cập**: Yêu cầu JWT (`KHACH_HANG`, `DUOC_SI`, `ADMIN`). Lấy `userId` từ claim `sub`.
- **Query Parameter**: `limit` (mặc định: `5`, min: `1`, max: `20`).
- **Nghiệp vụ & An toàn Dược phẩm**:
  - Lấy các thuốc khách đã từng mua từ `user_purchase_history`.
  - Tìm các thuốc hay mua kèm trong `product_cooccurrence`, loại bỏ các thuốc khách đã từng mua.
  - **AN TOÀN TUYỆT ĐỐI**: **Không bao giờ gợi ý thuốc kê đơn (`ETC`)**; chỉ giữ lại thuốc `OTC` và `active = true`.
  - **Fallback**: Nếu người dùng mới chưa từng mua hàng hoặc danh sách lọc bị rỗng, tự động chuyển sang Top thuốc bán chạy nhất từ `product_stats` (cũng loại bỏ `ETC`, chỉ lấy `OTC` active).
  - Thuộc tính `reason`: `"FOR_YOU"` (nếu từ gợi ý cá nhân hóa) hoặc `"BEST_SELLER"` (nếu fallback sang bán chạy).
- **Response thành công (`200 OK`)**:
  ```json
  [
    {
      "medicineId": "6ad23be6c034632667baaa03",
      "name": "Vitamin C 500mg Boston",
      "price": 45000.00,
      "type": "OTC",
      "requiresPrescription": false,
      "reason": "FOR_YOU"
    }
  ]
  ```

#### (C) Gợi ý thuốc cùng danh mục (Related by Category)
- **Path**: `GET /api/v1/recommendations/related/{medicineId}?limit=5`
- **Quyền truy cập**: Public (không yêu cầu JWT).
- **Query Parameter**: `limit` (mặc định: `5`, min: `1`, max: `20`).
- **Nghiệp vụ**:
  - Tra cứu `categoryId` của thuốc từ `medicine-service` (`GET /api/v1/medicines/{medicineId}`).
  - Lấy các thuốc cùng danh mục từ `medicine-service` (`GET /api/v1/medicines?categoryId={categoryId}`), loại trừ chính `medicineId`.
  - Giữ cả OTC và ETC (ETC có `requiresPrescription = true`), chỉ lấy thuốc active. Thuộc tính `reason`: `"SAME_CATEGORY"`.

---

## 3. Hợp Đồng Sự Kiện Apache Kafka (`localhost:9092`)

- **Broker**: `localhost:9092`
- **Key/Value Serializer**: `StringSerializer` / `StringDeserializer` (UTF-8 JSON).
- **Message Key**: Sử dụng ID của thực thể gốc (`orderId`, `prescriptionId`, hoặc `userId` dạng chuỗi) để đảm bảo các sự kiện của cùng một đơn hàng/người dùng luôn vào cùng 1 partition theo đúng thứ tự thời gian.
- **Vỏ chung khuyến nghị cho mọi Event Payload**: Mọi JSON event đều chứa `eventId` (`UUID` chống xử lý trùng ở consumer), `eventType`, `occurredAt` (`ISO-8601 Instant`) và `correlationId`.

### Bảng tổng hợp 6 Kafka Topics
*(Tên topic dùng thống nhất dấu chấm `.` theo đúng tài liệu `docs/B3..B6.md`)*

| STT | Tên Topic | Service Phát (Producer) | Service Nhận (Consumer) | Mục đích nghiệp vụ |
| :---: | :--- | :--- | :--- | :--- |
| 1 | `order.created` | `order-service` | `notification-service`, `recommendation-service`, `inventory-service` | Thông báo đặt hàng thành công, cập nhật gợi ý thuốc mua kèm, trừ tồn kho. |
| 2 | `order.status-changed` | `order-service` | `notification-service`, `recommendation-service` | Gửi thông báo/email cho khách; recommendation-service cập nhật co-occurrence và stats khi đơn HOAN_THANH, xóa đơn tạm khi DA_HUY. |
| 3 | `payment.completed` | `payment-service` *(Outbox)* | `order-service`, `notification-service` | Cập nhật trạng thái thanh toán của đơn hàng và gửi biên lai email cho khách. |
| 4 | `payment.failed` | `payment-service` *(Outbox)* | `order-service`, `notification-service` | Ghi nhận thanh toán thất bại để khách thanh toán lại hoặc hủy đơn. |
| 5 | `prescription.reviewed` | `prescription-service` | `notification-service` | Thông báo cho khách biết đơn thuốc đã được dược sĩ duyệt (`DA_DUYET`) hay từ chối (`TU_CHOI`). |
| 6 | `user.password-reset-requested` | `auth-service` | `notification-service` | Gửi email chứa mã/link đặt lại mật khẩu (TTL từ Redis) qua MailHog. |

---

### Cấu trúc JSON chi tiết của từng Event

#### 3.1. Topic `order.created`
- **Producer**: `order-service`
- **Consumers**: `notification-service`, `recommendation-service`, `inventory-service`
```json
{
  "eventId": "111e8400-e29b-41d4-a716-446655440001",
  "eventType": "order.created",
  "occurredAt": "2026-10-17T10:15:30Z",
  "correlationId": "corr-8899-aabb",
  "orderId": "990e8400-e29b-41d4-a716-446655440222",
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "customerEmail": "khachhang@pharmacy.local",
  "customerName": "Nguyễn Văn A",
  "prescriptionId": "770e8400-e29b-41d4-a716-446655440111",
  "status": "CHO_XU_LY",
  "totalAmount": 570000.00,
  "items": [
    {
      "medicineId": "6ad23be6c034632667baaa05",
      "medicineName": "Augmentin 1g",
      "prescriptionType": "ETC",
      "unitPrice": 285000.00,
      "quantity": 2
    }
  ]
}
```

#### 3.2. Topic `order.status-changed`
- **Producer**: `order-service`
- **Consumer**: `notification-service`
```json
{
  "eventId": "222e8400-e29b-41d4-a716-446655440002",
  "eventType": "order.status-changed",
  "occurredAt": "2026-10-17T11:00:00Z",
  "correlationId": "corr-8899-aabb",
  "orderId": "990e8400-e29b-41d4-a716-446655440222",
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "customerEmail": "khachhang@pharmacy.local",
  "customerName": "Nguyễn Văn A",
  "oldStatus": "CHO_XU_LY",
  "newStatus": "DANG_GIAO",
  "reason": "Đơn hàng đã được bàn giao cho đơn vị vận chuyển"
}
```

#### 3.3. Topic `payment.completed`
- **Producer**: `payment-service` (qua bảng `outbox`)
- **Consumers**: `order-service`, `notification-service`
```json
{
  "eventId": "333e8400-e29b-41d4-a716-446655440003",
  "eventType": "payment.completed",
  "occurredAt": "2026-10-17T10:18:00Z",
  "correlationId": "corr-8899-aabb",
  "paymentId": "bb0e8400-e29b-41d4-a716-446655440444",
  "orderId": "990e8400-e29b-41d4-a716-446655440222",
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "customerEmail": "khachhang@pharmacy.local",
  "amount": 570000.00,
  "paymentMethod": "VNPAY",
  "externalTransactionId": "VNP14567890"
}
```

#### 3.4. Topic `payment.failed`
- **Producer**: `payment-service` (qua bảng `outbox`)
- **Consumers**: `order-service`, `notification-service`
```json
{
  "eventId": "444e8400-e29b-41d4-a716-446655440004",
  "eventType": "payment.failed",
  "occurredAt": "2026-10-17T10:18:00Z",
  "correlationId": "corr-8899-aabb",
  "paymentId": "bb0e8400-e29b-41d4-a716-446655440444",
  "orderId": "990e8400-e29b-41d4-a716-446655440222",
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "customerEmail": "khachhang@pharmacy.local",
  "amount": 570000.00,
  "paymentMethod": "VNPAY",
  "errorCode": "24",
  "reason": "Khách hàng hủy giao dịch trên cổng thanh toán VNPay"
}
```

#### 3.5. Topic `prescription.reviewed`
- **Producer**: `prescription-service`
- **Consumer**: `notification-service`
```json
{
  "eventId": "555e8400-e29b-41d4-a716-446655440005",
  "eventType": "prescription.reviewed",
  "occurredAt": "2026-10-17T09:45:00Z",
  "correlationId": "corr-7766-ccdd",
  "prescriptionId": "770e8400-e29b-41d4-a716-446655440111",
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "customerEmail": "khachhang@pharmacy.local",
  "customerName": "Nguyễn Văn A",
  "pharmacistId": "22222222-2222-2222-2222-222222222222",
  "status": "DA_DUYET",
  "rejectionReason": null,
  "expiresAt": "2026-10-30T23:59:59Z",
  "approvedItems": [
    {
      "medicineId": "6ad23be6c034632667baaa05",
      "maxAllowedQuantity": 2
    }
  ]
}
```

#### 3.6. Topic `user.password-reset-requested`
- **Producer**: `auth-service`
- **Consumer**: `notification-service`
```json
{
  "eventId": "666e8400-e29b-41d4-a716-446655440006",
  "eventType": "user.password-reset-requested",
  "occurredAt": "2026-10-17T08:30:00Z",
  "correlationId": "corr-1122-eeff",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "khachhang@pharmacy.local",
  "fullName": "Nguyễn Văn A",
  "resetToken": "c9f8e7d6b5a443219876543210fedcba",
  "expiresInMinutes": 15
}
```

---

## 4. Các Điểm Cần Chốt Quyết Định (Không Tự Đoán)

Khi đối chiếu giữa các file `docs/B2..B6.md` và `application.properties` cũ của các service, có **4 điểm cần bạn quyết định** để các bước B2–B6 áp dụng đồng bộ 100%:

1. **Quy tắc tái sử dụng Đơn thuốc (`prescription_order_links`)**:
   - **Phương án A (Đơn giản, an toàn cho đồ án - Khuyên dùng)**: Mỗi đơn thuốc đã duyệt (`DA_DUYET`) chỉ được gắn với **1 đơn hàng hợp lệ duy nhất** (trừ khi đơn hàng đó bị hủy `DA_HUY` thì đơn thuốc được giải phóng để dùng lại).
   - **Phương án B**: Một đơn thuốc được mua làm nhiều đơn hàng nhỏ miễn tổng số lượng lũy kế không vượt quá `maxAllowedQuantity`.
2. **Hành vi của `order-service` khi nhận event `payment.completed` và `payment.failed`**:
   - Trong `GEMINI.md`, danh sách trạng thái đơn hàng chỉ có 5 giá trị: `CHO_XU_LY`, `DANG_GIAO`, `HOAN_THANH`, `DA_HUY`, `HOAN_TRA` (không có trạng thái `DA_THANH_TOAN` riêng).
   - **Câu hỏi**: Bạn muốn trong bảng `orders` có thêm một cột riêng `payment_status` (`CHUA_THANH_TOAN` / `DA_THANH_TOAN` / `THANH_TOAN_LOI`) giữ nguyên `status = CHO_XU_LY` chờ dược sĩ/admin giao hàng, hay khi `payment.failed` thì tự động chuyển đơn sang `DA_HUY`?
3. **Cách lấy `customerEmail` để `notification-service` gửi email**:
   - Trong JWT (`docs/JWT-CONTRACT.md`) đã có sẵn claim `email` và `name` của người dùng đang đăng nhập.
   - Tuy nhiên, khi **dược sĩ** duyệt đơn thuốc (`prescription.reviewed`) hoặc khi **VNPay IPN callback** gọi vào `payment-service` (`payment.completed`), người gọi lúc đó là Dược sĩ hoặc máy chủ VNPay (không phải JWT của khách hàng).
   - **Phương án A (Khuyên dùng)**: Khi khách hàng tạo đơn thuốc (`prescriptions`) hoặc tạo đơn hàng (`orders`), service lưu luôn snapshot `customer_email` và `customer_name` (đọc từ JWT của khách lúc tạo) vào bảng `prescriptions` / `orders`, sau đó đính kèm luôn `customerEmail` vào Kafka event để `notification-service` gửi mail ngay mà không cần gọi ngược sang `auth-service`.
   - **Phương án B**: Thêm một API nội bộ `GET /api/v1/users/{userId}` bên `auth-service` để `notification-service` gọi sang tra cứu email theo `userId`.
4. **Thống nhất tên Kafka Topic dùng dấu chấm (`.`) thay cho dấu gạch ngang (`-`)**:
   - Trong `docs/B3..B6.md` ghi tên topic dùng dấu chấm (`order.created`, `payment.completed`, `prescription.reviewed`, `user.password-reset-requested`, `order.status-changed`), còn trong `application.properties` khung cũ của `order-service`/`payment-service`/`notification-service` đang để `order-created` và `payment-completed`.
   - Khi triển khai tới bước B4, B5, B6, mình sẽ chuẩn hóa toàn bộ `application.properties` của các service đó sang **dấu chấm (`.`)** theo đúng `docs/B3..B6.md` và file `docs/API-CONTRACT.md` này nhé?
