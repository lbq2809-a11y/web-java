package com.pharmacy.prescription.dto;

import com.pharmacy.prescription.entity.PrescriptionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PrescriptionResponse(
        UUID id,
        UUID customerId,
        String customerName,
        String customerEmail,
        String imagePath,
        String notes,
        PrescriptionStatus status,
        UUID pharmacistId,
        String pharmacistName,
        String rejectionReason,
        Instant expiresAt,
        Instant createdAt,
        Instant reviewedAt,
        Instant updatedAt,
        List<PrescriptionItemDto> items
) {
}

