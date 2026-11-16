package com.pharmacy.payment.dto;

public record VnPayReturnResponse(
    boolean verified,
    String responseCode,
    String transactionStatus,
    String transactionReference,
    String message
) {
}