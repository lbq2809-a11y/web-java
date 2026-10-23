package com.pharmacy.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BatchResponse(
        UUID id,
        String medicineId,
        String batchNumber,
        LocalDate expiryDate,
        int quantityIn,
        int quantityAvailable,
        BigDecimal costPrice,
        Instant receivedAt,
        Instant createdAt,
        Instant updatedAt
) {}

