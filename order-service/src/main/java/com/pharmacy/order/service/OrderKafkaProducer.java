package com.pharmacy.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.order.dto.OrderCreatedEvent;
import com.pharmacy.order.dto.OrderStatusChangedEvent;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

/**
 * Producer gửi sự kiện order.created và order.status-changed lên Apache Kafka
 */
@ApplicationScoped
public class OrderKafkaProducer {

    private static final Logger LOG = Logger.getLogger(OrderKafkaProducer.class);

    @Inject
    @Channel("order-created")
    Emitter<Record<String, String>> orderCreatedEmitter;

    @Inject
    @Channel("order-status-changed")
    Emitter<Record<String, String>> statusChangedEmitter;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Phát sự kiện order.created với key là orderId (chuỗi UUID)
     */
    public void sendOrderCreatedEvent(OrderCreatedEvent event) {
        if (event == null || event.orderId() == null) {
            LOG.warn("Bỏ qua phát event Kafka vì dữ liệu event hoặc orderId là null");
            return;
        }

        String messageKey = event.orderId().toString();
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            orderCreatedEmitter.send(Record.of(messageKey, jsonPayload));
            LOG.infof("Đã phát sự kiện Kafka order.created (key=%s, total=%s)",
                    messageKey, event.totalAmount());
        } catch (Exception e) {
            LOG.errorf("Lỗi khi phát sự kiện Kafka order.created (key=%s): %s",
                    messageKey, e.getMessage(), e);
        }
    }

    /**
     * Phát sự kiện order.status-changed với key là orderId (chuỗi UUID)
     */
    public void sendOrderStatusChangedEvent(OrderStatusChangedEvent event) {
        if (event == null || event.orderId() == null) {
            LOG.warn("Bỏ qua phát event Kafka vì dữ liệu event hoặc orderId là null");
            return;
        }

        String messageKey = event.orderId().toString();
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            statusChangedEmitter.send(Record.of(messageKey, jsonPayload));
            LOG.infof("Đã phát sự kiện Kafka order.status-changed (key=%s, %s -> %s)",
                    messageKey, event.oldStatus(), event.newStatus());
        } catch (Exception e) {
            LOG.errorf("Lỗi khi phát sự kiện Kafka order.status-changed (key=%s): %s",
                    messageKey, e.getMessage(), e);
        }
    }
}
