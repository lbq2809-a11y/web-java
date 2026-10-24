package com.pharmacy.recommendation.document;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;

/**
 * Ma trận đồng xuất hiện giữa các cặp thuốc cùng được mua trong một đơn hàng hoàn thành (Product Co-occurrence).
 * Được lưu hai chiều: A -> B và B -> A.
 */
@MongoEntity(collection = "product_cooccurrence")
public class ProductCooccurrenceDocument extends PanacheMongoEntity {

    /** Mã thuốc gốc A */
    public String medicineId;

    /** Mã thuốc mua kèm B */
    public String coMedicineId;

    /** Tần suất xuất hiện chung trong các đơn hoàn thành */
    public long count;

    /** Thời điểm cập nhật cuối */
    public Instant updatedAt;
}

