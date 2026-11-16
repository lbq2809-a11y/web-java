package com.pharmacy.consultation.repository;

import com.mongodb.client.model.Filters;
import com.pharmacy.consultation.entity.KnowledgeChunkDocument;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

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

    /**
     * Tìm kiếm các chunk theo danh sách từ khóa triệu chứng/công dụng trên trường text và medicineName.
     */
    public List<KnowledgeChunkDocument> searchByKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        List<Bson> keywordFilters = new ArrayList<>();
        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) {
                continue;
            }
            Pattern regex = Pattern.compile(Pattern.quote(kw.trim()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            keywordFilters.add(Filters.or(
                    Filters.regex("text", regex),
                    Filters.regex("medicineName", regex)
            ));
        }
        if (keywordFilters.isEmpty()) {
            return List.of();
        }
        Bson query = Filters.and(
                Filters.in("section", List.of("indications", "general", "composition")),
                Filters.or(keywordFilters)
        );
        List<KnowledgeChunkDocument> results = new ArrayList<>();
        mongoCollection().find(query).limit(100).into(results);
        return results;
    }
}
