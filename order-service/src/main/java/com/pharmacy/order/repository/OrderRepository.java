package com.pharmacy.order.repository;

import com.pharmacy.order.entity.Order;
import com.pharmacy.order.entity.OrderStatus;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class OrderRepository implements PanacheRepositoryBase<Order, UUID> {

    /**
     * Tìm đơn hàng theo ID kèm nạp sẵn danh sách items và địa chỉ giao hàng
     */
    public Optional<Order> findByIdWithDetails(UUID id) {
        return find("SELECT DISTINCT o FROM Order o " +
                "LEFT JOIN FETCH o.items " +
                "LEFT JOIN FETCH o.shippingAddress " +
                "WHERE o.id = ?1", id)
                .firstResultOptional();
    }

    /**
     * Tìm đơn hàng theo khóa Idempotency để chống tạo đơn trùng lặp
     */
    public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return find("idempotencyKey", idempotencyKey.trim()).firstResultOptional();
    }

    /**
     * Lấy danh sách đơn hàng của khách hàng (hỗ trợ phân trang và lọc theo trạng thái)
     */
    public List<Order> findByCustomerId(UUID customerId, OrderStatus status, int page, int size) {
        PanacheQuery<Order> query;
        if (status != null) {
            query = find("customerId = ?1 and status = ?2", Sort.by("createdAt").descending(), customerId, status);
        } else {
            query = find("customerId = ?1", Sort.by("createdAt").descending(), customerId);
        }
        return query.page(Page.of(page, size)).list();
    }

    /**
     * Đếm tổng số đơn hàng của khách hàng
     */
    public long countByCustomerId(UUID customerId, OrderStatus status) {
        if (status != null) {
            return count("customerId = ?1 and status = ?2", customerId, status);
        }
        return count("customerId = ?1", customerId);
    }
}

