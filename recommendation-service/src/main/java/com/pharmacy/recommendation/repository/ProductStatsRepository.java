package com.pharmacy.recommendation.repository;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.pharmacy.recommendation.document.ProductStatsDocument;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ProductStatsRepository implements PanacheMongoRepository<ProductStatsDocument> {

    /**
     * Tăng số lượt mua thành công của thuốc lên 1.
     */
    public void incrementPurchaseCount(String medicineId) {
        mongoCollection().updateOne(
                Filters.eq("medicineId", medicineId),
                Updates.combine(
                        Updates.inc("purchaseCount", 1L),
                        Updates.set("lastPurchasedAt", Instant.now()),
                        Updates.setOnInsert("medicineId", medicineId)
                ),
                new UpdateOptions().upsert(true)
        );
    }

    /**
     * Lấy danh sách thuốc bán chạy nhất (purchaseCount giảm dần).
     */
    public List<ProductStatsDocument> findTopSelling(int limit) {
        List<ProductStatsDocument> result = new ArrayList<>();
        mongoCollection()
                .find()
                .sort(Sorts.descending("purchaseCount"))
                .limit(limit)
                .into(result);
        return result;
    }
}

