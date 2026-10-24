package com.pharmacy.recommendation.repository;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.pharmacy.recommendation.document.UserPurchaseHistoryDocument;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class UserPurchaseHistoryRepository implements PanacheMongoRepository<UserPurchaseHistoryDocument> {

    public Optional<UserPurchaseHistoryDocument> findByUserId(String userId) {
        return find("userId", userId).firstResultOptional();
    }

    /**
     * Bổ sung các thuốc đã mua vào lịch sử của người dùng bằng toán tử $addToSet ($each),
     * đảm bảo danh sách purchasedMedicineIds không bao giờ bị trùng lặp phần tử.
     */
    public void addPurchasedMedicines(String userId, List<String> medicineIds) {
        if (medicineIds == null || medicineIds.isEmpty()) {
            return;
        }
        mongoCollection().updateOne(
                Filters.eq("userId", userId),
                Updates.combine(
                        Updates.addEachToSet("purchasedMedicineIds", medicineIds),
                        Updates.set("lastPurchasedAt", Instant.now()),
                        Updates.setOnInsert("userId", userId)
                ),
                new UpdateOptions().upsert(true)
        );
    }
}

