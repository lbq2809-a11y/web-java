# B5. payment-service (PostgreSQL)

Bước B5: tạo payment-service dùng PostgreSQL (payment_db), cổng 8085.
1. Bảng và migration: payments (id, order_id, số tiền NUMERIC(19,2), phương thức COD/VNPAY/MOMO/QR, trạng thái, mã giao dịch ngoài, created_at), payment_transactions (log mỗi lần gọi/callback, lưu payload thô), unique constraint chống tạo trùng thanh toán cho một đơn.
2. COD: ghi nhận khi giao hàng thành công (ADMIN/shipper xác nhận đã thu tiền).
3. VNPay sandbox: tạo URL thanh toán (ký HMAC SHA512 theo tài liệu VNPay), trang return, endpoint IPN callback: kiểm tra chữ ký, kiểm tra số tiền khớp, xử lý idempotent (callback gọi nhiều lần không được cộng trùng), trả đúng mã phản hồi VNPay yêu cầu. TMN code và secret lấy từ biến môi trường. (Momo sandbox làm tương tự nếu tôi yêu cầu sau.)
4. Thanh toán QR: sinh mã QR cho đơn (nội dung tham chiếu tới payment id), kèm cách xác nhận (callback hoặc xác nhận thủ công bởi admin trong môi trường đồ án).
5. Sau khi có kết quả, phát event payment.completed / payment.failed lên Kafka; dùng Outbox pattern (bảng outbox + job đẩy lên Kafka) để không mất event.
6. Test: callback hợp lệ, sai chữ ký, callback lặp, số tiền sai.
