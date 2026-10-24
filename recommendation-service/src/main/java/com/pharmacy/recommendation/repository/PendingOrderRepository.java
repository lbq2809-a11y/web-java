package com.pharmacy.recommendation.repository;

import com.pharmacy.recommendation.document.PendingOrderDocument;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class PendingOrderRepository implements PanacheMongoRepository<PendingOrderDocument> {

    public Optional<PendingOrderDocument> findByOrderId(String orderId) {
        return find("orderId", orderId).firstResultOptional();
    }

    public void deleteByOrderId(String orderId) {
        delete("orderId", orderId);
    }
}

