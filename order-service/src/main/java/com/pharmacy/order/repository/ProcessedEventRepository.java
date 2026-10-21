package com.pharmacy.order.repository;

import com.pharmacy.order.entity.ProcessedEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class ProcessedEventRepository implements PanacheRepositoryBase<ProcessedEvent, UUID> {

    public boolean isEventProcessed(UUID eventId) {
        if (eventId == null) {
            return false;
        }
        return count("eventId", eventId) > 0;
    }
}

