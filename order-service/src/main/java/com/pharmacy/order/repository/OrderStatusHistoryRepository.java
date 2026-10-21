package com.pharmacy.order.repository;

import com.pharmacy.order.entity.OrderStatusHistory;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class OrderStatusHistoryRepository implements PanacheRepositoryBase<OrderStatusHistory, UUID> {

    public List<OrderStatusHistory> findByOrderId(UUID orderId) {
        return list("order.id = ?1 ORDER BY createdAt ASC", orderId);
    }
}

