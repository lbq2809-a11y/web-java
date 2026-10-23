package com.pharmacy.inventory.dto;

import java.util.List;

public record StockCheckResponse(
        boolean allAvailable,
        List<StockCheckItemResult> items
) {}

