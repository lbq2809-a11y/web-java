package com.pharmacy.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShippingAddressRequest(
        @NotBlank(message = "Tên người nhận không được để trống")
        @Size(max = 255, message = "Tên người nhận tối đa 255 ký tự")
        String recipientName,

        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^(0[3|5|7|8|9])+([0-9]{8})$", message = "Số điện thoại không đúng định dạng số di động Việt Nam (10 chữ số)")
        String phone,

        @NotBlank(message = "Địa chỉ nhận hàng không được để trống")
        @Size(max = 500, message = "Địa chỉ nhận hàng tối đa 500 ký tự")
        String addressLine
) {
}

