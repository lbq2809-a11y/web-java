package com.pharmacy.recommendation.repository;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.pharmacy.recommendation.document.ProductCooccurrenceDocument;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ProductCooccurrenceRepository implements PanacheMongoRepository<ProductCooccurrenceDocument> {

    /**
     * Tăng tần suất đồng xuất hiện (co-occurrence) giữa 2 thuốc.
     * Sử dụng upsert atomic của MongoDB để đảm bảo tính an toàn đồng thời.
     */
    public void incrementCooccurrence(String medicineId, String coMedicineId) {
        mongoCollection().updateOne(
                Filters.and(Filters.eq("medicineId", medicineId), Filters.eq("coMedicineId", coMedicineId)),
                Updates.combine(
                        Updates.inc("count", 1L),
                        Updates.set("updatedAt", Instant.now()),
                        Updates.setOnInsert("medicineId", medicineId),
                        Updates.setOnInsert("coMedicineId", coMedicineId)
                ),
                new UpdateOptions().upsert(true)
        );
    }

    /**
     * Tìm các thuốc hay mua kèm nhất với medicineId truyền vào.
     * Sắp xếp: count giảm dần, tie-break theo coMedicineId tăng dần.
     */
    public List<ProductCooccurrenceDocument> findTopCooccurring(String medicineId, int limit) {
        List<ProductCooccurrenceDocument> result = new ArrayList<>();
        mongoCollection()
                .find(Filters.eq("medicineId", medicineId))
                .sort(Sorts.orderBy(Sorts.descending("count"), Sorts.ascending("coMedicineId")))
                .limit(limit)
                .into(result);
        return result;
    }

    /**
     * Tìm tất cả các liên kết mua kèm đối với một tập hợp thuốc gốc (phục vụ gợi ý cá nhân hóa).
     */
    public List<ProductCooccurrenceDocument> findCooccurringForMedicines(List<String> medicineIds) {
        if (medicineIds == null || medicineIds.isEmpty()) {
            return List.of();
        }
        List<ProductCooccurrenceDocument> result = new ArrayList<>();
        mongoCollection()
                .find(Filters.in("medicineId", medicineIds))
                .sort(Sorts.descending("count"))
                .into(result);
        return result;
    }
}

