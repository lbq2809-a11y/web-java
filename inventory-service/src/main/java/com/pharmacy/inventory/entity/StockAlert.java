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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Thực thể ghi nhận các cảnh báo đã phát trong ngày (chống phát lặp)
 */
@Entity
@Table(name = "stock_alerts")
public class StockAlert extends PanacheEntityBase {

    public static final UUID DEFAULT_BATCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 30)
    public AlertType alertType;

    @Column(name = "medicine_id", nullable = false, length = 24)
    public String medicineId;

    @Column(name = "batch_id", nullable = false)
    public UUID batchId;

    @Column(name = "alert_date", nullable = false)
    public LocalDate alertDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (this.batchId == null) {
            this.batchId = DEFAULT_BATCH_ID;
        }
        if (this.alertDate == null) {
            this.alertDate = LocalDate.now();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}

