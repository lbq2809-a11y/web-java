package com.pharmacy.prescription.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.prescription.dto.PrescriptionReviewedEvent;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

/**
 * Producer gửi sự kiện prescription.reviewed lên Apache Kafka qua SmallRye Reactive Messaging
 */
@ApplicationScoped
public class PrescriptionKafkaProducer {

    private static final Logger LOG = Logger.getLogger(PrescriptionKafkaProducer.class);

    @Inject
    @Channel("prescription-reviewed")
    Emitter<Record<String, String>> emitter;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Gửi sự kiện đơn thuốc đã được duyệt/từ chối với key là prescriptionId (chuỗi UUID)
     */
    public void sendPrescriptionReviewed(PrescriptionReviewedEvent event) {
        if (event == null || event.prescriptionId() == null) {
            LOG.warn("Bỏ qua phát event Kafka vì dữ liệu event hoặc prescriptionId là null");
            return;
        }

        String messageKey = event.prescriptionId().toString();
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            emitter.send(Record.of(messageKey, jsonPayload));
            LOG.infof("Đã phát sự kiện Kafka prescription.reviewed (key=%s, status=%s)",
                    messageKey, event.status());
        } catch (Exception e) {
            LOG.errorf("Lỗi khi tuần tự hóa hoặc gửi sự kiện Kafka prescription.reviewed (key=%s): %s",
                    messageKey, e.getMessage(), e);
        }
    }
}

