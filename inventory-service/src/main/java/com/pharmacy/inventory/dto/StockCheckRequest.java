package com.pharmacy.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record StockCheckRequest(
        @NotEmpty(message = "Danh sách mặt hàng kiểm tra không được để trống")
        @Valid
        List<StockCheckItem> items
) {}

