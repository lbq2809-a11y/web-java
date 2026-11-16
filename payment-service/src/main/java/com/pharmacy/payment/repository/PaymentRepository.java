package com.pharmacy.payment.repository;

import com.pharmacy.payment.entity.Payment;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import java.util.Optional;

@ApplicationScoped
public class PaymentRepository implements PanacheRepositoryBase<Payment, UUID> {
    public Optional<Payment> findByOrderId(UUID orderId) {
        return find("orderId", orderId).firstResultOptional();
    }
}