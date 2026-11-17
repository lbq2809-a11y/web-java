package com.pharmacy.payment.repository;

import com.pharmacy.payment.entity.PaymentTransaction;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class PaymentTransactionRepository
        implements PanacheRepositoryBase<PaymentTransaction, UUID> {
}