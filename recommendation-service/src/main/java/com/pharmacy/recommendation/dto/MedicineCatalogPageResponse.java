package com.pharmacy.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * DTO hứng kết quả phân trang từ medicine-service (GET /api/v1/medicines).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MedicineCatalogPageResponse(
        List<MedicineResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {}
