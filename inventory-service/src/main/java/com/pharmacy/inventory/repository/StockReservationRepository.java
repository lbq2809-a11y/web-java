package com.pharmacy.inventory.repository;

import com.pharmacy.inventory.entity.ReservationStatus;
import com.pharmacy.inventory.entity.StockReservation;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class StockReservationRepository implements PanacheRepositoryBase<StockReservation, UUID> {

    public List<StockReservation> findByOrderId(UUID orderId) {
        return find("orderId = ?1", orderId).list();
    }

    public List<StockReservation> findByOrderIdAndStatus(UUID orderId, ReservationStatus status) {
        return find("orderId = ?1 and status = ?2", orderId, status).list();
    }

    public Optional<StockReservation> findByOrderAndBatch(UUID orderId, String medicineId, UUID batchId) {
        return find("orderId = ?1 and medicineId = ?2 and batchId = ?3", orderId, medicineId, batchId).firstResultOptional();
    }
}

