package com.pharmacy.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddToCartRequest(
        @NotBlank(message = "medicineId không được để trống")
        @Size(min = 24, max = 24, message = "medicineId phải là chuỗi hex ObjectId 24 ký tự của MongoDB")
        String medicineId,

        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng mua tối thiểu là 1")
        Integer quantity
) {
}

