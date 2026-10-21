package com.pharmacy.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event Apache Kafka phát lên topic order.created khi đơn hàng được tạo thành công
 * Khớp 100% với mục 3.1 của docs/API-CONTRACT.md
 */
public record OrderCreatedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        UUID orderId,
        UUID customerId,
        String customerEmail,
        String customerName,
        UUID prescriptionId,
        String status,
        BigDecimal totalAmount,
        List<OrderItemEvent> items
) {
    public record OrderItemEvent(
            String medicineId,
            String medicineName,
            String prescriptionType,
            BigDecimal unitPrice,
            Integer quantity
    ) {}
}

