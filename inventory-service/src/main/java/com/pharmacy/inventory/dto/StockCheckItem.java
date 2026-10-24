package com.pharmacy.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockCheckItem(
        @NotBlank(message = "medicineId không được để trống")
        @Size(min = 24, max = 24, message = "medicineId phải là 24 ký tự hex")
        String medicineId,

        @NotNull(message = "quantity không được để trống")
        @Min(value = 1, message = "quantity phải lớn hơn 0")
        Integer quantity
) {}

