package com.pharmacy.inventory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.inventory.dto.InventoryLowStockEvent;
import com.pharmacy.inventory.dto.InventoryNearExpiryEvent;
import com.pharmacy.inventory.entity.AlertType;
import com.pharmacy.inventory.entity.Batch;
import com.pharmacy.inventory.entity.OutboxEvent;
import com.pharmacy.inventory.entity.OutboxStatus;
import com.pharmacy.inventory.entity.StockAlert;
import com.pharmacy.inventory.repository.BatchRepository;
import com.pharmacy.inventory.repository.OutboxEventRepository;
import com.pharmacy.inventory.repository.StockAlertRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Job hằng ngày quét và phát cảnh báo thuốc sắp hết hạn và tồn kho thấp (Topic: inventory.near-expiry, inventory.low-stock)
 */
@ApplicationScoped
public class AlertScheduler {

    private static final Logger LOG = Logger.getLogger(AlertScheduler.class);

    @Inject
    BatchRepository batchRepository;

    @Inject
    StockAlertRepository stockAlertRepository;

    @Inject
    OutboxEventRepository outboxEventRepository;

    @Inject
    InventoryService inventoryService;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "inventory.alert.near-expiry-days", defaultValue = "90")
    int nearExpiryDays;

    @ConfigProperty(name = "inventory.alert.low-stock-threshold", defaultValue = "20")
    int lowStockThreshold;

    @Scheduled(cron = "{inventory.alert.cron:0 0 1 * * ?}")
    public void scheduledDailyScan() {
        runDailyAlertScan();
    }

    @Transactional
    public int runDailyAlertScan() {
        LocalDate today = LocalDate.now();
        LocalDate targetDate = today.plusDays(nearExpiryDays);
        int alertsGenerated = 0;

        // 1. Quét lô thuốc sắp hết hạn
        List<Batch> nearExpiryBatches = batchRepository.findNearExpiryBatches(today, targetDate);
        List<Batch> unalertedBatches = new ArrayList<>();
        for (Batch b : nearExpiryBatches) {
            if (!stockAlertRepository.existsAlertToday(AlertType.NEAR_EXPIRY, b.medicineId, b.id, today)) {
                unalertedBatches.add(b);
            }
        }

        // 2. Quét thuốc dưới ngưỡng tồn
        List<String> distinctMedicineIds = batchRepository.find("select distinct b.medicineId from Batch b")
                .project(String.class)
                .list();

        Map<String, Long> unalertedLowStockMap = new HashMap<>();
        for (String medId : distinctMedicineIds) {
            long totalAvailable = batchRepository.sumAvailableQuantity(medId, today);
            if (totalAvailable < lowStockThreshold) {
                if (!stockAlertRepository.existsAlertToday(AlertType.LOW_STOCK, medId, StockAlert.DEFAULT_BATCH_ID, today)) {
                    unalertedLowStockMap.put(medId, totalAvailable);
                }
            }
        }

        // Lấy danh sách tên thuốc từ medicine-service
        List<String> neededMedicineIds = new ArrayList<>(unalertedLowStockMap.keySet());
        for (Batch b : unalertedBatches) {
            if (!neededMedicineIds.contains(b.medicineId)) {
                neededMedicineIds.add(b.medicineId);
            }
        }
        Map<String, String> namesMap = inventoryService.fetchMedicineNames(neededMedicineIds);

        // Phát cảnh báo sắp hết hạn
        for (Batch b : unalertedBatches) {
            long daysLeft = ChronoUnit.DAYS.between(today, b.expiryDate);
            String medicineName = namesMap.get(b.medicineId);

            InventoryNearExpiryEvent event = InventoryNearExpiryEvent.of(
                    b.medicineId,
                    medicineName,
                    b.batchNumber,
                    b.expiryDate,
                    b.quantityAvailable,
                    daysLeft
            );

            createOutbox("INVENTORY", b.id.toString(), "inventory.near-expiry", "inventory.near-expiry", b.medicineId, event);

            StockAlert alert = new StockAlert();
            alert.alertType = AlertType.NEAR_EXPIRY;
            alert.medicineId = b.medicineId;
            alert.batchId = b.id;
            alert.alertDate = today;
            stockAlertRepository.persist(alert);

            alertsGenerated++;
            LOG.infof("Đã tạo cảnh báo lô sắp hết hạn: medicineId=%s, batch=%s, daysLeft=%d",
                    b.medicineId, b.batchNumber, daysLeft);
        }

        // Phát cảnh báo dưới ngưỡng tồn
        for (Map.Entry<String, Long> entry : unalertedLowStockMap.entrySet()) {
            String medId = entry.getKey();
            long qty = entry.getValue();
            String medicineName = namesMap.get(medId);

            InventoryLowStockEvent event = InventoryLowStockEvent.of(
                    medId,
                    medicineName,
                    qty,
                    lowStockThreshold
            );

            createOutbox("INVENTORY", medId, "inventory.low-stock", "inventory.low-stock", medId, event);

            StockAlert alert = new StockAlert();
            alert.alertType = AlertType.LOW_STOCK;
            alert.medicineId = medId;
            alert.batchId = StockAlert.DEFAULT_BATCH_ID;
            alert.alertDate = today;
            stockAlertRepository.persist(alert);

            alertsGenerated++;
            LOG.infof("Đã tạo cảnh báo tồn thấp: medicineId=%s, available=%d, threshold=%d",
                    medId, qty, lowStockThreshold);
        }

        return alertsGenerated;
    }

    private void createOutbox(String aggType, String aggId, String eventType, String topic, String key, Object payloadObj) {
        try {
            String json = objectMapper.writeValueAsString(payloadObj);
            OutboxEvent outbox = new OutboxEvent();
            outbox.aggregateType = aggType;
            outbox.aggregateId = aggId;
            outbox.eventType = eventType;
            outbox.topic = topic;
            outbox.messageKey = key;
            outbox.payload = json;
            outbox.status = OutboxStatus.PENDING;
            outbox.retryCount = 0;
            outboxEventRepository.persist(outbox);
        } catch (JsonProcessingException e) {
            LOG.errorf("Lỗi serialize event cảnh báo: %s", e.getMessage());
        }
    }
}

