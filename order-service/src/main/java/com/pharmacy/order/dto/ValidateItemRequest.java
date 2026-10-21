package com.pharmacy.order.dto;

public record ValidateItemRequest(
        String medicineId,
        Integer quantity
) {
}

