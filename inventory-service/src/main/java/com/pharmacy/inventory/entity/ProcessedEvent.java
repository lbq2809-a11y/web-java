package com.pharmacy.inventory.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Thực thể ghi nhận các event Kafka đã xử lý nhằm đảm bảo tính Idempotent
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    public String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    public String eventType;

    @Column(name = "order_id")
    public UUID orderId;

    @Column(name = "result_status", length = 50)
    public String resultStatus;

    @Column(name = "processed_at", nullable = false, updatable = false)
    public Instant processedAt;

    @PrePersist
    public void prePersist() {
        if (this.processedAt == null) {
            this.processedAt = Instant.now();
        }
    }
}

