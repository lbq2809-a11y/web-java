package com.pharmacy.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StockAdjustmentRequest(
        @NotNull(message = "Mã lô (batchId) không được để trống")
        UUID batchId,

        @NotNull(message = "Số lượng điều chỉnh (adjustmentQuantity) không được để trống")
        Integer adjustmentQuantity,

        @NotBlank(message = "Lý do điều chỉnh kho không được để trống")
        String reason
) {}

