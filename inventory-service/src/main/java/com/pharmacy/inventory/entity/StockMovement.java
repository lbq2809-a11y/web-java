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
 * Thực thể ghi nhận lịch sử biến động kho (Sổ cái kho / Audit Log)
 */
@Entity
@Table(name = "stock_movements")
public class StockMovement extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "medicine_id", nullable = false, length = 24)
    public String medicineId;

    @Column(name = "batch_id", nullable = false)
    public UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    public MovementType type;

    @Column(name = "quantity", nullable = false)
    public int quantity;

    @Column(name = "order_id")
    public UUID orderId;

    @Column(name = "reason")
    public String reason;

    @Column(name = "created_by")
    public String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}

