package com.pharmacy.order.dto;

import java.time.Instant;
import java.util.UUID;

public record LinkOrderResponse(
        UUID id,
        UUID prescriptionId,
        UUID orderId,
        Instant linkedAt
) {
}

