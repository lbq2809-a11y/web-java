package com.pharmacy.medicine.document;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Document lưu trữ thông tin thuốc trong collection "medicines" (MongoDB medicine_db).
 * Sử dụng Reference (categoryId) kết hợp Extended Reference (categoryName) để tối ưu tốc độ truy vấn.
 */
@MongoEntity(collection = "medicines")
public class MedicineDocument extends PanacheMongoEntity {

    /** Mã thuốc duy nhất (VD: MED-OTC-001, MED-ETC-001) */
    public String code;

    /** Tên thương mại của thuốc */
    public String name;

    /** Thành phần hoạt chất và hàm lượng */
    public String activeIngredient;

    /** Nhà sản xuất và quốc gia */
    public String manufacturer;

    /** Đơn vị tính (Hộp, Vỉ, Chai, Tuýp...) */
    public String unit;

    /** Giá bán theo đơn vị tính (BigDecimal được lưu dưới dạng Decimal128 trong MongoDB) */
    public BigDecimal price;

    /** Đường dẫn hình ảnh minh họa */
    public String imageUrl;

    /** Chỉ định điều trị (mô tả y khoa ngắn gọn, thận trọng) */
    public String indications;

    /** Chống chỉ định */
    public String contraindications;

    /** Liều lượng và cách dùng tham khảo */
    public String dosage;

    /** Phân loại thuốc: OTC (mua tự do) hoặc ETC (bắt buộc có đơn thuốc được duyệt) */
    public PrescriptionType prescriptionType;

    /** ID danh mục (Reference tới CategoryDocument.id dạng chuỗi hex 24 ký tự) */
    public String categoryId;

    /** Tên danh mục lưu kèm (Extended Reference) để tránh phải nối bảng khi đọc danh sách */
    public String categoryName;

    /** Trạng thái hiển thị (true: đang kinh doanh, false: ngừng kinh doanh / đã ẩn) */
    public boolean active = true;

    /** Điểm đánh giá trung bình (1.0 - 5.0), được tính bằng MongoDB Aggregation trên collection reviews */
    public double averageRating = 0.0;

    /** Tổng số lượt đánh giá, được tính bằng MongoDB Aggregation trên collection reviews */
    public int reviewCount = 0;

    public Instant createdAt;

    public Instant updatedAt;
}

