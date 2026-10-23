package com.pharmacy.inventory.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Thực thể bảng Outbox phục vụ Transactional Outbox Pattern
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    public String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    public String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    public String eventType;

    @Column(name = "topic", nullable = false, length = 100)
    public String topic;

    @Column(name = "message_key", nullable = false, length = 100)
    public String messageKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    public String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    public int retryCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "sent_at")
    public Instant sentAt;

    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = OutboxStatus.PENDING;
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}

