package com.pharmacy.inventory.dto;

import com.pharmacy.inventory.entity.MovementType;

import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        String medicineId,
        UUID batchId,
        MovementType type,
        int quantity,
        UUID orderId,
        String reason,
        String createdBy,
        Instant createdAt
) {}

