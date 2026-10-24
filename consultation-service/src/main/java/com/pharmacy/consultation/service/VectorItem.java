package com.pharmacy.consultation.service;

import java.util.List;

/**
 * Bản ghi vector nhúng lưu trong bộ nhớ RAM phục vụ tính toán Cosine Similarity.
 */
public record VectorItem(
        String chunkId,
        String medicineId,
        String medicineName,
        String section,
        String text,
        List<Double> vector,
        String contentHash
) {
}

