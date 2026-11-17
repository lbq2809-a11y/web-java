package com.pharmacy.payment.dto;

public record VnPayIpnResponse(
    String RspCode,
    String Message
) {
}