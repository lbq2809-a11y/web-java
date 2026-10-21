package com.pharmacy.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CheckoutRequest(
        /**
         * Mã đơn thuốc đã được dược sĩ duyệt (Bắt buộc nếu trong giỏ có ít nhất 1 thuốc ETC)
         */
        UUID prescriptionId,

        @NotNull(message = "Thông tin địa chỉ giao hàng không được để trống")
        @Valid
        ShippingAddressRequest shippingAddress,

        @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
        String notes
) {
}

