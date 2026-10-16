# B4. order-service (PostgreSQL + Redis)

Bước B4: tạo order-service dùng PostgreSQL (order_db) + Redis cho giỏ hàng, cổng 8084.
1. Giỏ hàng trên Redis (key theo userId, TTL hợp lý): thêm/sửa số lượng/xóa/xem giỏ. Khi thêm, gọi medicine-service (REST Client) lấy thông tin thuốc; giỏ chỉ lưu id và số lượng, giá lấy lại khi checkout.
2. Bảng và migration: orders, order_items (lưu SNAPSHOT tên thuốc, giá, loại ETC/OTC tại thời điểm đặt), order_status_history, shipping_address (snapshot). Trạng thái: CHO_XU_LY, DANG_GIAO, HOAN_THANH, DA_HUY, HOAN_TRA.
3. Đặt hàng (checkout): lấy giỏ -> gọi medicine-service lấy giá mới nhất -> nếu có thuốc ETC thì bắt buộc request kèm prescriptionId và gọi prescription-service kiểm tra hợp lệ (đã duyệt, còn hạn, đúng khách, đúng thuốc, đúng số lượng); nếu không hợp lệ trả lỗi rõ ràng liệt kê thuốc nào thiếu đơn -> tạo đơn trong một transaction -> xóa giỏ -> liên kết đơn thuốc với đơn hàng -> phát event order.created.
4. Chống đặt trùng: hỗ trợ header Idempotency-Key.
5. API: xem đơn của tôi, lịch sử, chi tiết; ADMIN/DUOC_SI cập nhật trạng thái theo luồng hợp lệ (không nhảy trạng thái bậy); khách hủy khi còn CHO_XU_LY; yêu cầu hoàn trả sau khi HOAN_THANH.
6. Lắng nghe event payment.completed / payment.failed từ Kafka để cập nhật trạng thái.
7. Dùng Fault Tolerance (timeout, retry, circuit breaker) cho các lời gọi sang service khác. Có test tích hợp (mock các service ngoài).
