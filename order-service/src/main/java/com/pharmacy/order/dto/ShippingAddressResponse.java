package com.pharmacy.order.dto;

import java.time.Instant;
import java.util.UUID;

public record ShippingAddressResponse(
        UUID id,
        String recipientName,
        String phone,
        String addressLine,
        Instant createdAt
) {
}

