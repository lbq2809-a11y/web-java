package com.pharmacy.inventory.dto;

import java.time.Instant;
import java.util.UUID;

public record InventoryLowStockEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        String medicineId,
        String medicineName,
        long availableQuantity,
        int threshold
) {
    public static InventoryLowStockEvent of(String medicineId, String medicineName, long availableQuantity, int threshold) {
        return new InventoryLowStockEvent(
                UUID.randomUUID().toString(),
                "inventory.low-stock",
                Instant.now(),
                null,
                medicineId,
                medicineName,
                availableQuantity,
                threshold
        );
    }
}

