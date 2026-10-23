# B8. inventory-service (PostgreSQL)

Bước B8: hoàn thiện inventory-service dùng PostgreSQL (inventory_db), cổng 8087.
Đọc @GEMINI.md, @docs/API-CONTRACT.md, @docs/JWT-CONTRACT.md và code medicine-service (cách cấu hình JWT, test). Chỉ sửa trong thư mục inventory-service.
Bối cảnh: service đã có pom.xml và application.properties (inventory_db, cổng 8087), CHƯA có migration. Code đăng nhập từng bị chép nhầm vào đây đã bị xóa, KHÔNG thêm lại đăng nhập; chỉ verify JWT như medicine-service.

1. Bảng và migration Flyway V1:
   - batches (id UUID, medicine_id kiểu chuỗi là ObjectId của medicine-service, batch_number, expiry_date, quantity_in, quantity_available, cost_price NUMERIC(19,2), received_at), unique (medicine_id, batch_number).
   - stock_movements (id, medicine_id, batch_id, type IMPORT/EXPORT/RESERVE/RELEASE/ADJUST, quantity, order_id nullable, reason, created_at, created_by).
   - stock_reservations (id, order_id, medicine_id, batch_id, quantity, status RESERVED/COMMITTED/RELEASED, created_at), unique (order_id, medicine_id, batch_id) để chống xử lý trùng.
   Vẽ ERD bằng chữ trong kế hoạch.
2. API (ADMIN và DUOC_SI quản lý; khách không gọi): nhập lô mới, xem tồn theo thuốc (tổng và theo lô), danh sách lô (lọc theo thuốc, sắp hết hạn), điều chỉnh kho có lý do, xem lịch sử xuất nhập. API nội bộ kiểm tra tồn nếu API-CONTRACT yêu cầu.
3. Giữ hàng theo nguyên tắc FEFO (lô hết hạn sớm nhất xuất trước), không lấy lô đã hết hạn, trong một transaction có khóa dòng (SELECT ... FOR UPDATE) để không bán vượt tồn khi nhiều đơn cùng lúc.
4. Kafka (đúng tên topic và cấu trúc trong API-CONTRACT): nghe order.created để giữ hàng; nghe payment.failed và đơn bị hủy để nhả hàng; nghe payment.completed để chốt. Xử lý idempotent (nhận trùng event không trừ hai lần). Nếu API-CONTRACT CHƯA có event kho (giữ hàng thành công/thất bại) thì DỪNG LẠI, đề xuất cấu trúc event cho tôi duyệt, đừng tự đặt.
5. Job hằng ngày (quarkus-scheduler): tìm lô sắp hết hạn (mặc định 90 ngày, cấu hình được) và thuốc dưới ngưỡng tồn tối thiểu, phát event cảnh báo cho notification-service theo hợp đồng, và có API liệt kê cảnh báo.
6. Test: nhập lô, giữ hàng đúng FEFO, hết hàng, hai đơn cùng giành lô cuối cùng (chỉ một đơn thắng), event lặp không trừ hai lần, nhả hàng khi đơn hủy, lô hết hạn không bị xuất.
Đưa KẾ HOẠCH trước, tôi duyệt rồi mới code. Sau code chạy ./mvnw test và báo kết quả thật.
