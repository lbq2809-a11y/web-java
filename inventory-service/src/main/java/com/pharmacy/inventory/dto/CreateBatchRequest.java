package com.pharmacy.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateBatchRequest(
        @NotBlank(message = "Mã thuốc (medicineId) không được để trống")
        @Size(min = 24, max = 24, message = "Mã thuốc phải là ObjectId đúng 24 ký tự hex")
        String medicineId,

        @NotBlank(message = "Số lô (batchNumber) không được để trống")
        String batchNumber,

        @NotNull(message = "Ngày hết hạn (expiryDate) không được để trống")
        @Future(message = "Ngày hết hạn của lô mới nhập phải ở tương lai")
        LocalDate expiryDate,

        @NotNull(message = "Số lượng nhập không được để trống")
        @Min(value = 1, message = "Số lượng nhập phải lớn hơn hoặc bằng 1")
        Integer quantity,

        @NotNull(message = "Giá vốn (costPrice) không được để trống")
        @DecimalMin(value = "0.0", message = "Giá vốn không được âm")
        BigDecimal costPrice
) {}

