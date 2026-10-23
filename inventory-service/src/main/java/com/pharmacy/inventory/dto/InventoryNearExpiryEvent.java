package com.pharmacy.inventory.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryNearExpiryEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        String medicineId,
        String medicineName,
        String batchNumber,
        LocalDate expiryDate,
        int quantity,
        long daysLeft
) {
    public static InventoryNearExpiryEvent of(
            String medicineId,
            String medicineName,
            String batchNumber,
            LocalDate expiryDate,
            int quantity,
            long daysLeft
    ) {
        return new InventoryNearExpiryEvent(
                UUID.randomUUID().toString(),
                "inventory.near-expiry",
                Instant.now(),
                null,
                medicineId,
                medicineName,
                batchNumber,
                expiryDate,
                quantity,
                daysLeft
        );
    }
}

