package com.pharmacy.inventory.dto;

public record StockCheckItemResult(
        String medicineId,
        int requested,
        long available,
        boolean sufficient
) {}

