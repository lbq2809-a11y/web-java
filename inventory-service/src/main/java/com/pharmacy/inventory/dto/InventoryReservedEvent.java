package com.pharmacy.inventory.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryReservedEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        UUID orderId,
        List<ReservedItem> items
) {
    public static InventoryReservedEvent of(String correlationId, UUID orderId, List<ReservedItem> items) {
        return new InventoryReservedEvent(
                UUID.randomUUID().toString(),
                "inventory.reserved",
                Instant.now(),
                correlationId,
                orderId,
                items
        );
    }
}

