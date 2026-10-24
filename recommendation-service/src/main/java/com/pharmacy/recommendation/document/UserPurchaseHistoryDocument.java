package com.pharmacy.recommendation.document;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;
import java.util.List;

/**
 * Lịch sử các loại thuốc người dùng đã từng mua thành công.
 * Dùng để loại bỏ các thuốc đã mua khỏi gợi ý cá nhân hóa.
 */
@MongoEntity(collection = "user_purchase_history")
public class UserPurchaseHistoryDocument extends PanacheMongoEntity {

    /** Mã người dùng (UUID dạng chuỗi) */
    public String userId;

    /** Tập hợp các medicineId mà người dùng này đã từng mua (không trùng lặp nhờ $addToSet) */
    public List<String> purchasedMedicineIds;

    /** Thời điểm mua đơn hàng cuối */
    public Instant lastPurchasedAt;
}

