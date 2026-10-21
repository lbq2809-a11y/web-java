package com.pharmacy.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event Apache Kafka nhận từ topic payment.failed khi thanh toán thất bại
 * Khớp 100% mục 3.4 trong docs/API-CONTRACT.md
 */
public record PaymentFailedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        UUID paymentId,
        UUID orderId,
        UUID customerId,
        String customerEmail,
        BigDecimal amount,
        String paymentMethod,
        String errorCode,
        String reason
) {
}

