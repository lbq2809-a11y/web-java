package com.pharmacy.consultation.repository;

import com.pharmacy.consultation.entity.RagCacheDocument;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

/**
 * Kho lưu trữ bộ đệm câu trả lời RAG trong MongoDB.
 */
@ApplicationScoped
public class RagCacheRepository implements PanacheMongoRepository<RagCacheDocument> {

    public Optional<RagCacheDocument> findByCacheKey(String cacheKey) {
        if (cacheKey == null || cacheKey.isBlank()) {
            return Optional.empty();
        }
        return find("cacheKey", cacheKey).firstResultOptional();
    }

    public long deleteByMedicineId(String medicineId) {
        if (medicineId == null || medicineId.isBlank()) {
            return 0;
        }
        return delete("retrievedMedicineIds", medicineId);
    }
}

