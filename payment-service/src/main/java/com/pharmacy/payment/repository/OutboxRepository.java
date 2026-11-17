package com.pharmacy.payment.repository;

import com.pharmacy.payment.entity.OutboxEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class OutboxRepository implements PanacheRepositoryBase<OutboxEvent, UUID> {

    @Inject
    EntityManager entityManager;

    public List<OutboxEvent> findUnpublishedForUpdate(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        List<?> rows = entityManager.createNativeQuery("""
                SELECT *
                FROM outbox_events
                WHERE published_at IS NULL
                ORDER BY created_at ASC
                FOR UPDATE SKIP LOCKED
                """, OutboxEvent.class)
            .setMaxResults(limit)
            .getResultList();

        return rows.stream()
            .map(OutboxEvent.class::cast)
            .toList();
    }
}