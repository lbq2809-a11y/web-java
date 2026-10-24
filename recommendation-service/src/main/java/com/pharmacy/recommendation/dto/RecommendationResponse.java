package com.pharmacy.recommendation.dto;

import java.math.BigDecimal;

/**
 * Định dạng phản hồi cho từng loại thuốc được gợi ý:
 * - reason: FREQUENTLY_BOUGHT_TOGETHER, FOR_YOU, BEST_SELLER, SAME_CATEGORY
 */
public record RecommendationResponse(
        String medicineId,
        String name,
        BigDecimal price,
        String type,
        boolean requiresPrescription,
        String reason
) {}

