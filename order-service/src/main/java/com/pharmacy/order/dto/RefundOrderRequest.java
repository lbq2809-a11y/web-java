package com.pharmacy.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundOrderRequest(
        @NotBlank(message = "Lý do yêu cầu hoàn trả không được để trống")
        @Size(max = 1000, message = "Lý do hoàn trả tối đa 1000 ký tự")
        String reason
) {
}
