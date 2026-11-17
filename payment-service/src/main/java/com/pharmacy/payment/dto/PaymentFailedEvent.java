package com.pharmacy.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
    String externalTransactionId,
    String errorCode,
    String reason
) {
}