package com.pharmacy.recommendation.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO nhận thông tin thuốc trả về từ medicine-service (cổng 8081).
 */
public record MedicineResponse(
        String id,
        String code,
        String name,
        String activeIngredient,
        String manufacturer,
        String unit,
        BigDecimal price,
        String imageUrl,
        String indications,
        String contraindications,
        String dosage,
        String prescriptionType,
        String categoryId,
        String categoryName,
        boolean active,
        Double averageRating,
        Integer reviewCount,
        Instant createdAt,
        Instant updatedAt
) {}

