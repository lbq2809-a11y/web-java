package com.pharmacy.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderSnapshot(
    UUID id,
    UUID customerId,
    String customerEmail,
    BigDecimal totalAmount,
    String status
) {
}