package com.pharmacy.inventory.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderStatusChangedEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        UUID orderId,
        UUID customerId,
        String customerEmail,
        String customerName,
        String oldStatus,
        String newStatus,
        String reason
) {}

