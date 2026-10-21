package com.pharmacy.prescription.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApproveItemRequest(
        @NotBlank(message = "medicineId không được để trống")
        @Size(min = 24, max = 24, message = "medicineId phải là chuỗi ObjectId 24 ký tự hex của MongoDB")
        String medicineId,

        String medicineName,

        @NotNull(message = "maxAllowedQuantity không được để trống")
        @Min(value = 1, message = "Số lượng cho phép phải lớn hơn 0")
        Integer maxAllowedQuantity
) {
}

