package com.pharmacy.order.dto;

import java.util.List;

public record OrderPageResponse(
        List<OrderResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}

