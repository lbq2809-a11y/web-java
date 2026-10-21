package com.pharmacy.order.dto;

import java.math.BigDecimal;

public record CartItemResponse(
        String medicineId,
        String medicineCode,
        String medicineName,
        String unit,
        BigDecimal price,
        String imageUrl,
        String prescriptionType, // OTC hoặc ETC
        Integer quantity,
        BigDecimal subtotal,
        boolean active
) {
}

