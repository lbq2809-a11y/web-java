# B1. medicine-service (MongoDB)

Bước B1: tạo medicine-service dùng MongoDB (medicine_db), cổng 8081. Làm theo thứ tự, xong mỗi bước dừng lại để tôi kiểm tra:
1. Lệnh tạo project, danh sách extension, application.properties.
2. Thiết kế collection: medicines (tên, mã, giá, đơn vị, hình ảnh, nhà sản xuất, thành phần, chỉ định, chống chỉ định, cách dùng, loại ETC/OTC, danh mục, trạng thái ẩn/hiện, điểm đánh giá trung bình), categories, reviews (userId, medicineId, số sao, nội dung, thời gian). Giải thích chọn embed hay reference và vì sao. Đề xuất index (text index tìm kiếm, index danh mục, giá).
3. Entity Panache, repository, DTO, service.
4. REST API: CRUD thuốc, CRUD danh mục, tìm kiếm theo từ khóa, lọc theo danh mục/loại ETC-OTC/khoảng giá, sắp xếp, phân trang; API tạo và liệt kê review (mỗi user một review cho một thuốc, cập nhật điểm trung bình).
5. Phân quyền: ai cũng xem được; chỉ ADMIN (và DUOC_SI nếu hợp lý) được thêm/sửa/xóa; đọc role từ JWT. Chưa cần auth-service chạy thật: hướng dẫn tạo token thử.
6. Dữ liệu mẫu (khoảng 30 thuốc thật phổ biến ở Việt Nam, đủ ETC/OTC) và test tích hợp bằng Testcontainers.
