package com.pharmacy.prescription.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Yêu cầu từ order-service gửi sang để gắn (liên kết) đơn thuốc vào đơn hàng
 */
public record LinkOrderRequest(
        @NotNull(message = "orderId không được để trống")
        UUID orderId,

        @NotNull(message = "customerId không được để trống")
        UUID customerId
) {
}

