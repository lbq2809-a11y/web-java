package com.pharmacy.order.mapper;

import com.pharmacy.order.dto.OrderItemResponse;
import com.pharmacy.order.dto.OrderResponse;
import com.pharmacy.order.dto.OrderStatusHistoryResponse;
import com.pharmacy.order.dto.ShippingAddressResponse;
import com.pharmacy.order.entity.Order;
import com.pharmacy.order.entity.OrderItem;
import com.pharmacy.order.entity.OrderStatusHistory;
import com.pharmacy.order.entity.ShippingAddress;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class OrderMapper {

    public OrderResponse toOrderResponse(Order order) {
        if (order == null) {
            return null;
        }

        List<OrderItemResponse> itemResponses = Collections.emptyList();
        if (order.getItems() != null) {
            itemResponses = order.getItems().stream()
                    .map(this::toOrderItemResponse)
                    .toList();
        }

        ShippingAddressResponse addressResponse = null;
        if (order.getShippingAddress() != null) {
            addressResponse = toShippingAddressResponse(order.getShippingAddress());
        }

        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getPrescriptionId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getIdempotencyKey(),
                order.getNotes(),
                addressResponse,
                itemResponses,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public OrderItemResponse toOrderItemResponse(OrderItem item) {
        if (item == null) {
            return null;
        }
        return new OrderItemResponse(
                item.getId(),
                item.getMedicineId(),
                item.getMedicineCode(),
                item.getMedicineName(),
                item.getUnit(),
                item.getPrescriptionType(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getSubtotal(),
                item.getCreatedAt()
        );
    }

    public ShippingAddressResponse toShippingAddressResponse(ShippingAddress address) {
        if (address == null) {
            return null;
        }
        return new ShippingAddressResponse(
                address.getId(),
                address.getRecipientName(),
                address.getPhone(),
                address.getAddressLine(),
                address.getCreatedAt()
        );
    }

    public OrderStatusHistoryResponse toOrderStatusHistoryResponse(OrderStatusHistory history) {
        if (history == null) {
            return null;
        }
        return new OrderStatusHistoryResponse(
                history.getId(),
                history.getOldStatus(),
                history.getNewStatus(),
                history.getChangedBy(),
                history.getReason(),
                history.getCreatedAt()
        );
    }
}

