package com.pharmacy.payment.dto;

import com.pharmacy.payment.entity.PaymentMethod;
import com.pharmacy.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    UUID orderId,
    BigDecimal amount,
    PaymentMethod method,
    PaymentStatus status,
    String externalTransactionId,
    String redirectUrl,
    String qrContent,
    Instant createdAt
) {
}