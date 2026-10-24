package com.pharmacy.inventory.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Thực thể quản lý từng lô thuốc nhập về kho
 */
@Entity
@Table(name = "batches")
public class Batch extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "medicine_id", nullable = false, length = 24)
    public String medicineId;

    @Column(name = "batch_number", nullable = false, length = 100)
    public String batchNumber;

    @Column(name = "expiry_date", nullable = false)
    public LocalDate expiryDate;

    @Column(name = "quantity_in", nullable = false)
    public int quantityIn;

    @Column(name = "quantity_available", nullable = false)
    public int quantityAvailable;

    @Column(name = "cost_price", nullable = false, precision = 19, scale = 2)
    public BigDecimal costPrice;

    @Column(name = "received_at", nullable = false)
    public Instant receivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (this.receivedAt == null) {
            this.receivedAt = now;
        }
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

