package com.pharmacy.inventory.dto;

import java.util.List;

public record StockResponse(
        String medicineId,
        long totalAvailable,
        List<BatchResponse> batches
) {}

