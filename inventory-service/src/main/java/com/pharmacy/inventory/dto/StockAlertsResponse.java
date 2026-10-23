package com.pharmacy.inventory.dto;

import java.util.List;

public record StockAlertsResponse(
        List<NearExpiryAlertDto> nearExpiryAlerts,
        List<LowStockAlertDto> lowStockAlerts
) {}

