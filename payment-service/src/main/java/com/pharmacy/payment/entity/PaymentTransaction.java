package com.pharmacy.payment.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {
    @Id
    public UUID id;

    @Column(name = "payment_id", nullable = false)
    public UUID paymentId;

    @Column(name = "transaction_type", nullable = false)
    public String transactionType;

    @Column(name = "external_transaction_id")
    public String externalTransactionId;

    @Column(nullable = false)
    public String status;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    public String rawPayload;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public PaymentTransaction() {
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }
}