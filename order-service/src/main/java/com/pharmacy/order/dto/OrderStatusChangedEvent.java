package com.pharmacy.order.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Event Apache Kafka phát lên topic order.status-changed khi đơn hàng thay đổi trạng thái
 * Khớp 100% mục 3.2 trong docs/API-CONTRACT.md
 */
public record OrderStatusChangedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        UUID orderId,
        UUID customerId,
        String customerEmail,
        String customerName,
        String oldStatus,
        String newStatus,
        String reason
) {
}

