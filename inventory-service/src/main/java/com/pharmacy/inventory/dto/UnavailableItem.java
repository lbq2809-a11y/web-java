package com.pharmacy.inventory.dto;

public record UnavailableItem(
        String medicineId,
        int requested,
        long available
) {}

