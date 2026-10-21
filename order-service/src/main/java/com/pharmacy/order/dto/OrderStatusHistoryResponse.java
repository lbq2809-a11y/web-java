package com.pharmacy.order.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderStatusHistoryResponse(
        UUID id,
        String oldStatus,
        String newStatus,
        UUID changedBy,
        String reason,
        Instant createdAt
) {
}

