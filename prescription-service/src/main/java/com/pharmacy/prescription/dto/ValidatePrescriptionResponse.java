package com.pharmacy.prescription.dto;

import com.pharmacy.prescription.entity.PrescriptionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Phản hồi kiểm tra tính hợp lệ của đơn thuốc cho order-service
 */
public record ValidatePrescriptionResponse(
        UUID prescriptionId,
        UUID customerId,
        boolean valid,
        PrescriptionStatus status,
        Instant expiresAt,
        List<String> violations
) {
}

