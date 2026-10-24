package com.pharmacy.inventory.dto;

public record ReservedItem(
        String medicineId,
        int quantity
) {}

