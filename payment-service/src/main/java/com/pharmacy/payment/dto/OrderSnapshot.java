package com.pharmacy.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderSnapshot(
    UUID id,
    UUID customerId,
    String customerEmail,
    BigDecimal totalAmount,
    String status
) {
}