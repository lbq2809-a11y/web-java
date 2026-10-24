# B10. recommendation-service (MongoDB)

Bước B10: hoàn thiện recommendation-service dùng MongoDB (recommendation_db), cổng 8089.
Đọc @GEMINI.md, @docs/API-CONTRACT.md, @docs/JWT-CONTRACT.md và code medicine-service. Chỉ sửa trong thư mục recommendation-service. Đây là gợi ý dựa trên thống kê mua hàng (không phải mô hình AI); phần AI và RAG nằm ở chatbot-service.

1. Collections: product_cooccurrence (medicineId, relatedMedicineId, count, updatedAt; unique theo cặp), user_purchase_history (userId, medicineIds, lastPurchasedAt), product_stats (medicineId, purchaseCount). Đề xuất index và giải thích trong kế hoạch. medicineId là chuỗi (ObjectId) như medicine-service.
2. Kafka: nghe event đơn hàng theo API-CONTRACT để cập nhật thống kê (chỉ tính đơn đã hoàn thành, không tính đơn hủy). Xử lý idempotent: nhận trùng event không cộng hai lần. Nếu event hiện có thiếu danh sách thuốc trong đơn thì DỪNG LẠI, đề xuất cách bổ sung cho tôi duyệt (đổi event ở order-service là việc của người khác, đừng tự sửa).
3. API:
   - GET /api/v1/recommendations/also-bought/{medicineId}?limit= (công khai): «khách mua thuốc này cũng mua».
   - GET /api/v1/recommendations/for-me?limit= (cần JWT): gợi ý theo lịch sử mua của người dùng (userId từ claim sub). Người dùng mới chưa có lịch sử thì trả danh sách thuốc bán chạy.
   - GET /api/v1/recommendations/related/{medicineId}: cùng danh mục (gọi medicine-service).
   Dữ liệu thuốc lấy từ medicine-service qua API batch (đúng API-CONTRACT), có timeout và Fault Tolerance; medicine-service lỗi thì trả danh sách rút gọn, không lỗi 500.
4. An toàn: thuốc kê đơn (ETC) KHÔNG đưa vào gợi ý «for-me», và nếu xuất hiện ở «also-bought» thì đánh dấu rõ cần đơn thuốc. Không gợi ý thuốc đã ngừng kinh doanh (active=false).
5. Test: cập nhật thống kê từ event, event lặp, đơn hủy không tính, gợi ý cho người mới, ETC không lọt vào for-me, medicine-service sập vẫn trả kết quả.
Đưa KẾ HOẠCH trước, tôi duyệt rồi mới code. Sau code chạy ./mvnw test và báo kết quả thật.
