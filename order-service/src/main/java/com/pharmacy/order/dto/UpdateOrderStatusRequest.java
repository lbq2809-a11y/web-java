package com.pharmacy.order.dto;

import com.pharmacy.order.entity.OrderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateOrderStatusRequest(
        @NotNull(message = "Trạng thái mới không được để trống")
        OrderStatus newStatus,

        @NotBlank(message = "Lý do cập nhật trạng thái không được để trống")
        @Size(max = 1000, message = "Lý do tối đa 1000 ký tự")
        String reason
) {
}
