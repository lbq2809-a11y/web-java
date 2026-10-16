package com.pharmacy.medicine.document;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;

/**
 * Document lưu trữ danh mục nhóm thuốc trong collection "categories" (MongoDB medicine_db).
 */
@MongoEntity(collection = "categories")
public class CategoryDocument extends PanacheMongoEntity {

    /** Mã danh mục duy nhất (VD: CAT_ANALGESIC) */
    public String code;

    /** Tên hiển thị của danh mục */
    public String name;

    /** Mô tả ngắn gọn về nhóm thuốc */
    public String description;

    /** Trạng thái hiển thị (true: đang hoạt động, false: đã ẩn) */
    public boolean active = true;

    public Instant createdAt;

    public Instant updatedAt;
}

