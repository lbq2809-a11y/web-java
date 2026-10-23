package com.pharmacy.inventory.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.inventory.dto.OrderCreatedEvent;
import com.pharmacy.inventory.dto.OrderStatusChangedEvent;
import com.pharmacy.inventory.service.InventoryReservationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OrderKafkaConsumer {

    private static final Logger LOG = Logger.getLogger(OrderKafkaConsumer.class);

    @Inject
    InventoryReservationService reservationService;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("order-created-in")
    public void consumeOrderCreated(String payload) {
        LOG.infof("Nhận event order.created từ Kafka: %s", payload);
        try {
            OrderCreatedEvent event = objectMapper.readValue(payload, OrderCreatedEvent.class);
            reservationService.processOrderCreated(event);
        } catch (Exception e) {
            LOG.errorf("Lỗi khi xử lý event order.created: %s", e.getMessage(), e);
        }
    }

    @Incoming("order-status-changed-in")
    public void consumeOrderStatusChanged(String payload) {
        LOG.infof("Nhận event order.status-changed từ Kafka: %s", payload);
        try {
            OrderStatusChangedEvent event = objectMapper.readValue(payload, OrderStatusChangedEvent.class);
            reservationService.processOrderStatusChanged(event);
        } catch (Exception e) {
            LOG.errorf("Lỗi khi xử lý event order.status-changed: %s", e.getMessage(), e);
        }
    }
}

