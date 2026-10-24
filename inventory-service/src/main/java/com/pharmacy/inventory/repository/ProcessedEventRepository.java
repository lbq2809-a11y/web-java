package com.pharmacy.inventory.repository;

import com.pharmacy.inventory.entity.ProcessedEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ProcessedEventRepository implements PanacheRepositoryBase<ProcessedEvent, UUID> {

    public Optional<ProcessedEvent> findByEventId(String eventId) {
        return find("eventId = ?1", eventId).firstResultOptional();
    }

    public Optional<ProcessedEvent> findByOrderIdAndEventType(UUID orderId, String eventType) {
        return find("orderId = ?1 and eventType = ?2", orderId, eventType).firstResultOptional();
    }
}

