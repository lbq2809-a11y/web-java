# C. Prompt sửa lỗi, rà soát, bảo vệ đồ án

## Khi gặp lỗi
Tôi chạy bước <tên bước> và gặp lỗi sau. Hãy: (1) giải thích nguyên nhân bằng tiếng Việt dễ hiểu, (2) chỉ rõ file và dòng cần sửa, (3) đưa đoạn code sửa, (4) nói cách tôi kiểm tra lại. Không viết lại toàn bộ dự án.
Lệnh tôi chạy: <lệnh>
Lỗi nguyên văn: <dán log>

## Khi AI làm lệch hoặc làm thừa
Bạn đã làm sai/thừa ở chỗ: <mô tả>. Giữ nguyên mọi thứ khác, chỉ sửa đúng phần này theo GEMINI.md. Liệt kê các file bạn thay đổi.

## Rà soát chất lượng sau mỗi service
Hãy review service vừa làm như một người chấm đồ án khó tính. Kiểm tra: đúng công nghệ và CSDL đã chốt, đủ chức năng của service, phân quyền từng API, validate đầu vào, xử lý lỗi thống nhất, không hard-code bí mật, có migration/index, test pass, README đủ. Liệt kê vấn đề theo mức độ nghiêm trọng và đề xuất cách sửa, đừng sửa gì khi tôi chưa đồng ý.

## Chuẩn bị bảo vệ trước thầy
Dựa trên dự án này, hãy đóng vai thầy giáo hỏi vấn đáp. Đưa ra 15 câu hỏi khó về kiến trúc microservice, lý do chọn PostgreSQL vs MongoDB cho từng service, luồng đơn thuốc kê đơn, xử lý thanh toán callback, bảo mật JWT/QR/Face login. Với mỗi câu, cho tôi gợi ý câu trả lời ngắn gọn để tôi tự trả lời lại bằng lời của mình.

## Vẽ sơ đồ cho báo cáo
Viết mã Mermaid cho: (1) sơ đồ kiến trúc tổng thể các service và CSDL, (2) sơ đồ tuần tự luồng mua thuốc kê đơn từ upload đơn thuốc đến thanh toán, (3) ERD của order_db và prescription_db.
