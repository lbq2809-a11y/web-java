package com.pharmacy.inventory.dto;

public record LowStockAlertDto(
        String medicineId,
        String medicineName,
        long availableQuantity,
        int threshold
) {}

