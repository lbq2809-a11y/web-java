# B3. prescription-service (PostgreSQL)

Bước B3: tạo prescription-service dùng PostgreSQL (prescription_db), cổng 8083.
1. Thiết kế bảng và migration: prescriptions (id, customer_id, image_path, ghi chú, trạng thái CHO_DUYET/DA_DUYET/TU_CHOI, pharmacist_id, lý do từ chối, ngày hết hạn đơn, created_at, reviewed_at), prescription_items (thuốc được duyệt: medicine_id, số lượng tối đa cho phép), prescription_order_links (prescription_id, order_id). Vẽ ERD bằng chữ.
2. Upload ảnh đơn thuốc (multipart), kiểm tra loại file và dung lượng, lưu vào MinIO, DB chỉ lưu đường dẫn; API lấy ảnh có kiểm tra quyền (chỉ chủ đơn và dược sĩ).
3. API cho khách: tạo đơn thuốc, xem danh sách/lịch sử đơn của mình.
4. API cho dược sĩ: danh sách đơn chờ duyệt, xem chi tiết kèm ảnh, duyệt (chọn các thuốc và số lượng được phép) hoặc từ chối (bắt buộc ghi lý do).
5. API nội bộ để order-service kiểm tra "đơn thuốc X của khách Y có hợp lệ và còn hiệu lực cho thuốc Z, số lượng N không" và API gắn đơn thuốc vào đơn hàng.
6. Khi duyệt/từ chối, phát event Kafka (prescription.reviewed) để notification-service gửi thông báo.
7. Test, gồm các tình huống: dược sĩ duyệt đơn đã duyệt, khách xem đơn người khác, đơn hết hạn.
