package com.pharmacy.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.recommendation.dto.OrderCreatedEvent;
import com.pharmacy.recommendation.dto.OrderStatusChangedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class RecommendationConsumer {

    private static final Logger LOG = Logger.getLogger(RecommendationConsumer.class);

    @Inject
    RecommendationService recommendationService;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("order-created-in")
    public void consumeOrderCreated(String payload) {
        LOG.infof("Nhận event order.created từ Kafka: %s", payload);
        try {
            OrderCreatedEvent event = objectMapper.readValue(payload, OrderCreatedEvent.class);
            recommendationService.handleOrderCreated(event);
        } catch (Exception e) {
            // Fault-tolerance: Log cảnh báo và tiếp tục, không làm kẹt luồng consumer
            LOG.warnf("Lỗi khi đọc/xử lý event order.created (bỏ qua): %s", e.getMessage());
        }
    }

    @Incoming("order-status-changed-in")
    public void consumeOrderStatusChanged(String payload) {
        LOG.infof("Nhận event order.status-changed từ Kafka: %s", payload);
        try {
            OrderStatusChangedEvent event = objectMapper.readValue(payload, OrderStatusChangedEvent.class);
            recommendationService.handleOrderStatusChanged(event);
        } catch (Exception e) {
            // Fault-tolerance: Log cảnh báo và tiếp tục, không làm kẹt luồng consumer
            LOG.warnf("Lỗi khi đọc/xử lý event order.status-changed (bỏ qua): %s", e.getMessage());
        }
    }
}

