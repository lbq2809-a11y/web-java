package com.pharmacy.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        String medicineId,
        String medicineCode,
        String medicineName,
        String unit,
        String prescriptionType,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal subtotal,
        Instant createdAt
) {
}

