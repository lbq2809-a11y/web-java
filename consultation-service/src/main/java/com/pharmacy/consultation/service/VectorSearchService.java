package com.pharmacy.consultation.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Dịch vụ tính toán độ tương đồng Cosine Similarity trong RAM và truy xuất Top-K đoạn tri thức.
 */
@ApplicationScoped
public class VectorSearchService {

    @Inject
    VectorKnowledgeStore vectorStore;

    public record ScoredChunk(VectorItem item, double score) {}

    /**
     * Tìm kiếm Top-K đoạn tri thức tương đồng nhất với vector câu hỏi.
     * Sắp xếp xác định: điểm cosine giảm dần (Score DESC), nếu hòa điểm thì theo medicineId ASC, section ASC.
     */
    public List<ScoredChunk> search(List<Double> queryVector, int topK, double similarityThreshold) {
        if (queryVector == null || queryVector.isEmpty()) {
            return List.of();
        }

        List<VectorItem> allVectors = vectorStore.getAll();
        if (allVectors.isEmpty()) {
            return List.of();
        }

        List<ScoredChunk> candidates = new ArrayList<>();

        for (VectorItem item : allVectors) {
            double score = cosineSimilarity(queryVector, item.vector());
            if (score >= similarityThreshold) {
                candidates.add(new ScoredChunk(item, score));
            }
        }

        // Sắp xếp xác định (Deterministic Sorting)
        Comparator<ScoredChunk> comparator = (a, b) -> {
            // So sánh điểm giảm dần
            if (Math.abs(b.score() - a.score()) > 1e-6) {
                return Double.compare(b.score(), a.score());
            }
            // Hòa điểm: so sánh medicineId tăng dần
            String medA = a.item().medicineId() != null ? a.item().medicineId() : "";
            String medB = b.item().medicineId() != null ? b.item().medicineId() : "";
            int medComp = medA.compareTo(medB);
            if (medComp != 0) {
                return medComp;
            }
            // Hòa tiếp: so sánh section tăng dần
            String secA = a.item().section() != null ? a.item().section() : "";
            String secB = b.item().section() != null ? b.item().section() : "";
            return secA.compareTo(secB);
        };

        return candidates.stream()
                .sorted(comparator)
                .limit(topK > 0 ? topK : 4)
                .toList();
    }

    /**
     * Tính độ tương đồng Cosine giữa 2 vector.
     */
    public double cosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1 == null || v2 == null || v1.isEmpty() || v2.isEmpty()) {
            return 0.0;
        }

        int dim = Math.min(v1.size(), v2.size());
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < dim; i++) {
            double a = v1.get(i);
            double b = v2.get(i);
            dotProduct += a * b;
            norm1 += a * a;
            norm2 += b * b;
        }

        if (norm1 <= 0.0 || norm2 <= 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}

