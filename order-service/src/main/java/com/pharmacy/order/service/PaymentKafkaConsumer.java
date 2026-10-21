package com.pharmacy.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.order.dto.PaymentCompletedEvent;
import com.pharmacy.order.dto.PaymentFailedEvent;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/**
 * Consumer lắng nghe các sự kiện thanh toán từ payment-service qua Apache Kafka:
 * - payment.completed: Ghi nhận thanh toán thành công
 * - payment.failed: Hủy đơn nếu đơn đang ở trạng thái CHO_XU_LY
 * Đảm bảo tính Idempotent thông qua bảng processed_events
 */
@ApplicationScoped
public class PaymentKafkaConsumer {

    private static final Logger LOG = Logger.getLogger(PaymentKafkaConsumer.class);

    @Inject
    OrderService orderService;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("payment-completed-in")
    @Blocking
    public void consumePaymentCompleted(String payload) {
        LOG.debugf("Nhận message từ topic payment.completed: %s", payload);
        try {
            PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
            orderService.handlePaymentCompleted(event);
        } catch (Exception e) {
            LOG.errorf("Lỗi khi xử lý sự kiện payment.completed: %s (payload: %s)", e.getMessage(), payload, e);
        }
    }

    @Incoming("payment-failed-in")
    @Blocking
    public void consumePaymentFailed(String payload) {
        LOG.debugf("Nhận message từ topic payment.failed: %s", payload);
        try {
            PaymentFailedEvent event = objectMapper.readValue(payload, PaymentFailedEvent.class);
            orderService.handlePaymentFailed(event);
        } catch (Exception e) {
            LOG.errorf("Lỗi khi xử lý sự kiện payment.failed: %s (payload: %s)", e.getMessage(), payload, e);
        }
    }
}

