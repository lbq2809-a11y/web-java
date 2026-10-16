package com.pharmacy.medicine.document;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;

/**
 * Document lưu trữ đánh giá của người dùng cho từng thuốc trong collection "reviews".
 * Được đánh Compound Unique Index trên (medicineId, userId) để mỗi người dùng chỉ tạo được 1 đánh giá cho 1 thuốc.
 */
@MongoEntity(collection = "reviews")
public class ReviewDocument extends PanacheMongoEntity {

    /** ID của thuốc được đánh giá (chuỗi hex 24 ký tự của ObjectId) */
    public String medicineId;

    /** ID của người dùng đánh giá (UUID đọc từ claim "sub" trong JWT) */
    public String userId;

    /** Họ tên người dùng (đọc từ claim "name" trong JWT) */
    public String userFullName;

    /** Điểm đánh giá từ 1 đến 5 sao */
    public int rating;

    /** Nội dung nhận xét */
    public String comment;

    public Instant createdAt;

    public Instant updatedAt;
}

