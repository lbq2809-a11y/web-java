package com.pharmacy.prescription.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event Apache Kafka phát lên topic prescription.reviewed khi đơn thuốc được Dược sĩ duyệt hoặc từ chối
 * Khớp 100% với mục 3.5 trong docs/API-CONTRACT.md
 */
public record PrescriptionReviewedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        UUID prescriptionId,
        UUID customerId,
        String customerEmail,
        String customerName,
        UUID pharmacistId,
        String status,
        String rejectionReason,
        Instant expiresAt,
        List<ApprovedItemEvent> approvedItems
) {
    public record ApprovedItemEvent(
            String medicineId,
            Integer maxAllowedQuantity
    ) {}
}

