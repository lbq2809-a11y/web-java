package com.pharmacy.payment.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    public UUID id;

    @Column(name = "aggregate_id", nullable = false)
    public UUID aggregateId;

    @Column(name = "event_type", nullable = false)
    public String eventType;

    @Column(nullable = false)
    public String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String payload;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "published_at")
    public Instant publishedAt;

    @Column(nullable = false)
    public int attempts;

    @Column(name = "last_error", columnDefinition = "TEXT")
    public String lastError;

    public OutboxEvent() {
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }
}