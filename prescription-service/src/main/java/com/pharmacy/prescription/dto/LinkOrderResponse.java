package com.pharmacy.prescription.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Phản hồi sau khi gắn đơn thuốc vào đơn hàng
 */
public record LinkOrderResponse(
        UUID id,
        UUID prescriptionId,
        UUID orderId,
        Instant linkedAt
) {
}

