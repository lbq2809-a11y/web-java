# B6. notification-service (MongoDB)

Bước B6: tạo notification-service dùng MongoDB (notification_db), cổng 8086.
1. Collections: notifications (userId, loại, tiêu đề, nội dung, đã đọc, thời gian), email_logs (người nhận, template, trạng thái gửi, lỗi), templates.
2. Lắng nghe Kafka: user.password-reset-requested, order.created, order.status-changed, payment.completed, prescription.reviewed -> gửi email (quarkus-mailer, thử bằng MailHog localhost:1025) và lưu thông báo trong app.
3. Email dùng template HTML (Qute), có retry khi gửi lỗi.
4. API cho khách: danh sách thông báo, đánh dấu đã đọc, đếm chưa đọc.
5. (Tùy chọn) Nhắc lịch uống thuốc: khách đặt lịch (thuốc, giờ, số ngày), job định kỳ (quarkus-scheduler) tạo thông báo/email đúng giờ.
6. Test việc nhận event và tạo thông báo.
