package com.pharmacy.order.dto;

import com.pharmacy.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        String customerName,
        String customerEmail,
        UUID prescriptionId,
        OrderStatus status,
        BigDecimal totalAmount,
        String idempotencyKey,
        String notes,
        ShippingAddressResponse shippingAddress,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}

