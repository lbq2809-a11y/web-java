package com.pharmacy.order.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateCartItemRequest(
        @NotNull(message = "Số lượng không được để trống")
        Integer quantity
) {
}

