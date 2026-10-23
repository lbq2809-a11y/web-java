package com.pharmacy.inventory.repository;

import com.pharmacy.inventory.entity.OutboxEvent;
import com.pharmacy.inventory.entity.OutboxStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class OutboxEventRepository implements PanacheRepositoryBase<OutboxEvent, UUID> {

    public List<OutboxEvent> findPendingEvents(int limit) {
        return find("status = ?1 order by createdAt asc", OutboxStatus.PENDING)
                .page(Page.of(0, limit))
                .list();
    }
}

