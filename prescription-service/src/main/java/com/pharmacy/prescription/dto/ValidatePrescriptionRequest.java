package com.pharmacy.prescription.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Yêu cầu từ order-service gửi sang để kiểm tra tính hợp lệ của đơn thuốc cho các thuốc kê đơn (ETC)
 */
public record ValidatePrescriptionRequest(
        @NotNull(message = "customerId không được để trống")
        UUID customerId,

        @NotEmpty(message = "Danh sách thuốc cần kiểm tra không được để trống")
        @Valid
        List<ValidateItemRequest> items
) {
}

