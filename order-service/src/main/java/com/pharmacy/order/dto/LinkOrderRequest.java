package com.pharmacy.order.dto;

import java.util.UUID;

public record LinkOrderRequest(
        UUID orderId,
        UUID customerId
) {
}

