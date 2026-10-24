package com.pharmacy.recommendation.document;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;

/**
 * Thống kê tổng số lượt mua thành công của từng loại thuốc.
 * Phục vụ gợi ý "Top bán chạy nhất" (Best Sellers) khi người dùng mới chưa có lịch sử mua.
 */
@MongoEntity(collection = "product_stats")
public class ProductStatsDocument extends PanacheMongoEntity {

    /** Mã thuốc (ObjectId hex 24 ký tự) */
    public String medicineId;

    /** Tổng số lượt mua thành công */
    public long purchaseCount;

    /** Thời điểm mua gần nhất */
    public Instant lastPurchasedAt;
}

