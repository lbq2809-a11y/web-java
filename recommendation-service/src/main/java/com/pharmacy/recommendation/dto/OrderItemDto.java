package com.pharmacy.recommendation.dto;

import java.math.BigDecimal;

public record OrderItemDto(
        String medicineId,
        String medicineName,
        String prescriptionType,
        BigDecimal unitPrice,
        int quantity
) {}

