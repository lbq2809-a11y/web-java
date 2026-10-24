package com.pharmacy.inventory.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        UUID orderId,
        UUID customerId,
        String customerEmail,
        String customerName,
        UUID prescriptionId,
        String status,
        BigDecimal totalAmount,
        List<OrderCreatedItem> items
) {}

