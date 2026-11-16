package com.pharmacy.payment.dto;

import com.pharmacy.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreatePaymentRequest(
    @NotNull UUID orderId,
    @NotNull PaymentMethod method
) {
}