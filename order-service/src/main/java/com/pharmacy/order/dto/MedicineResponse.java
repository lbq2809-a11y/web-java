package com.pharmacy.order.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO nhận thông tin thuốc từ medicine-service
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
        String prescriptionType, // OTC hoặc ETC
        String categoryId,
        String categoryName,
        boolean active,
        Double averageRating,
        Integer reviewCount,
        Instant createdAt,
        Instant updatedAt
) {
}

