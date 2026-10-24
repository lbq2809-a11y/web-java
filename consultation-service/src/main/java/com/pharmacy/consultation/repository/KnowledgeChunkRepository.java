package com.pharmacy.consultation.repository;

import com.pharmacy.consultation.entity.KnowledgeChunkDocument;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

/**
 * Kho lưu trữ các đoạn tri thức thuốc trong MongoDB.
 */
@ApplicationScoped
public class KnowledgeChunkRepository implements PanacheMongoRepository<KnowledgeChunkDocument> {

    public Optional<KnowledgeChunkDocument> findByMedicineIdAndSection(String medicineId, String section) {
        return find("medicineId = ?1 and section = ?2", medicineId, section).firstResultOptional();
    }

    public List<KnowledgeChunkDocument> findByMedicineId(String medicineId) {
        return list("medicineId", medicineId);
    }
}

