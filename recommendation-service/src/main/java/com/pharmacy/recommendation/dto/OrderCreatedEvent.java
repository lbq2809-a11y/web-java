package com.pharmacy.recommendation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderCreatedEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        String orderId,
        String customerId,
        String customerEmail,
        String customerName,
        String prescriptionId,
        String status,
        BigDecimal totalAmount,
        List<OrderItemDto> items
) {}

