package com.pharmacy.inventory.dto;

public record StockSummaryResponse(
        String medicineId,
        long totalAvailable,
        int totalBatches
) {}

