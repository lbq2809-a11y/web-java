package com.pharmacy.inventory.service;

import com.pharmacy.inventory.entity.OutboxEvent;
import com.pharmacy.inventory.entity.OutboxStatus;
import com.pharmacy.inventory.repository.OutboxEventRepository;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;

/**
 * Job định kỳ quét bảng outbox_events và đẩy sang Apache Kafka (Transactional Outbox Pattern)
 */
@ApplicationScoped
public class OutboxScheduler {

    private static final Logger LOG = Logger.getLogger(OutboxScheduler.class);

    @Inject
    OutboxEventRepository outboxEventRepository;

    @Inject
    @Channel("inventory-reserved-out")
    Emitter<Record<String, String>> reservedEmitter;

    @Inject
    @Channel("inventory-reservation-failed-out")
    Emitter<Record<String, String>> reservationFailedEmitter;

    @Inject
    @Channel("inventory-low-stock-out")
    Emitter<Record<String, String>> lowStockEmitter;

    @Inject
    @Channel("inventory-near-expiry-out")
    Emitter<Record<String, String>> nearExpiryEmitter;

    @Scheduled(every = "{inventory.outbox.poll-interval:2s}")
    public void scheduledPollOutbox() {
        processPendingEvents();
    }

    @Transactional
    public int processPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents(50);
        if (pendingEvents.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (OutboxEvent event : pendingEvents) {
            try {
                dispatchToKafka(event);
                event.status = OutboxStatus.SENT;
                event.sentAt = Instant.now();
                outboxEventRepository.persist(event);
                count++;
                LOG.infof("Đã đẩy event outbox lên Kafka: id=%s, topic=%s, key=%s",
                        event.id, event.topic, event.messageKey);
            } catch (Exception e) {
                event.retryCount++;
                if (event.retryCount >= 5) {
                    event.status = OutboxStatus.FAILED;
                }
                outboxEventRepository.persist(event);
                LOG.errorf("Lỗi khi đẩy event outbox lên Kafka: id=%s, error=%s", event.id, e.getMessage());
            }
        }
        return count;
    }

    private void dispatchToKafka(OutboxEvent event) {
        Record<String, String> record = Record.of(event.messageKey, event.payload);
        switch (event.topic) {
            case "inventory.reserved" -> reservedEmitter.send(record);
            case "inventory.reservation-failed" -> reservationFailedEmitter.send(record);
            case "inventory.low-stock" -> lowStockEmitter.send(record);
            case "inventory.near-expiry" -> nearExpiryEmitter.send(record);
            default -> LOG.warnf("Topic không xác định trong outbox: %s", event.topic);
        }
    }
}

