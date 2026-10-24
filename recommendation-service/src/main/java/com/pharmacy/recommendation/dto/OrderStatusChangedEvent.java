package com.pharmacy.recommendation.dto;

import java.time.Instant;

public record OrderStatusChangedEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        String orderId,
        String customerId,
        String customerEmail,
        String customerName,
        String oldStatus,
        String newStatus,
        String reason
) {}

