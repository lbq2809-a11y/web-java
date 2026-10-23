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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Thực thể giữ hàng cho đơn hàng (Stock Reservation)
 */
@Entity
@Table(name = "stock_reservations")
public class StockReservation extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "order_id", nullable = false)
    public UUID orderId;

    @Column(name = "medicine_id", nullable = false, length = 24)
    public String medicineId;

    @Column(name = "batch_id", nullable = false)
    public UUID batchId;

    @Column(name = "quantity", nullable = false)
    public int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    public ReservationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}

