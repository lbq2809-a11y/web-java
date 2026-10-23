package com.pharmacy.inventory.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryReservationFailedEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        UUID orderId,
        String reason,
        List<UnavailableItem> unavailableItems
) {
    public static InventoryReservationFailedEvent of(String correlationId, UUID orderId, String reason, List<UnavailableItem> unavailableItems) {
        return new InventoryReservationFailedEvent(
                UUID.randomUUID().toString(),
                "inventory.reservation-failed",
                Instant.now(),
                correlationId,
                orderId,
                reason,
                unavailableItems
        );
    }
}

