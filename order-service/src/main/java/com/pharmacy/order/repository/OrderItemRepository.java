package com.pharmacy.order.repository;

import com.pharmacy.order.entity.OrderItem;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class OrderItemRepository implements PanacheRepositoryBase<OrderItem, UUID> {

    public List<OrderItem> findByOrderId(UUID orderId) {
        return list("order.id", orderId);
    }
}

