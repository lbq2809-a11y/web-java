package com.pharmacy.order.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ValidatePrescriptionResponse(
        UUID prescriptionId,
        UUID customerId,
        boolean valid,
        String status,
        Instant expiresAt,
        List<String> violations
) {
}

