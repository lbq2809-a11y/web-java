package com.pharmacy.inventory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.inventory.dto.InventoryReservationFailedEvent;
import com.pharmacy.inventory.dto.InventoryReservedEvent;
import com.pharmacy.inventory.dto.OrderCreatedEvent;
import com.pharmacy.inventory.dto.OrderCreatedItem;
import com.pharmacy.inventory.dto.OrderStatusChangedEvent;
import com.pharmacy.inventory.dto.ReservedItem;
import com.pharmacy.inventory.dto.UnavailableItem;
import com.pharmacy.inventory.entity.Batch;
import com.pharmacy.inventory.entity.MovementType;
import com.pharmacy.inventory.entity.OutboxEvent;
import com.pharmacy.inventory.entity.OutboxStatus;
import com.pharmacy.inventory.entity.ProcessedEvent;
import com.pharmacy.inventory.entity.ReservationStatus;
import com.pharmacy.inventory.entity.StockMovement;
import com.pharmacy.inventory.entity.StockReservation;
import com.pharmacy.inventory.repository.BatchRepository;
import com.pharmacy.inventory.repository.OutboxEventRepository;
import com.pharmacy.inventory.repository.ProcessedEventRepository;
import com.pharmacy.inventory.repository.StockMovementRepository;
import com.pharmacy.inventory.repository.StockReservationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class InventoryReservationService {

    private static final Logger LOG = Logger.getLogger(InventoryReservationService.class);

    @Inject
    BatchRepository batchRepository;

    @Inject
    StockReservationRepository reservationRepository;

    @Inject
    StockMovementRepository stockMovementRepository;

    @Inject
    OutboxEventRepository outboxEventRepository;

    @Inject
    ProcessedEventRepository processedEventRepository;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Xử lý event order.created trong một transaction:
     * 1. Idempotency: Kiểm tra eventId và orderId để chống giữ 2 lần.
     * 2. Gom nhóm số lượng theo medicineId và SẮP XẾP theo alphabet tăng dần để CHỐNG DEADLOCK khi nhiều đơn mua chéo nhau.
     * 3. Khóa dòng (SELECT ... FOR UPDATE) các lô chưa hết hạn theo thứ tự FEFO (expiryDate ASC, receivedAt ASC).
     * 4. Kiểm tra All-or-Nothing: Đủ mọi mặt hàng thì giữ hàng; thiếu bất kỳ mặt hàng nào thì KHÔNG giữ gì cả.
     * 5. Ghi outbox_events cùng transaction để job scheduler đẩy lên Kafka.
     */
    @Transactional
    public void processOrderCreated(OrderCreatedEvent event) {
        if (event == null || event.orderId() == null) {
            LOG.warn("Bỏ qua event order.created không hợp lệ (null hoặc thiếu orderId)");
            return;
        }

        // 1. Kiểm tra Idempotent theo eventId hoặc orderId
        if (event.eventId() != null && processedEventRepository.findByEventId(event.eventId()).isPresent()) {
            LOG.infof("Event order.created với eventId=%s đã được xử lý trước đó, bỏ qua để đảm bảo idempotent", event.eventId());
            return;
        }
        if (processedEventRepository.findByOrderIdAndEventType(event.orderId(), "order.created").isPresent()) {
            LOG.infof("Đơn hàng orderId=%s đã được xử lý order.created trước đó, bỏ qua", event.orderId());
            return;
        }

        if (event.items() == null || event.items().isEmpty()) {
            LOG.warnf("Đơn hàng orderId=%s không có sản phẩm nào để giữ kho", event.orderId());
            recordProcessedEvent(event.eventId(), "order.created", event.orderId(), "NO_ITEMS");
            return;
        }

        // 2. Gom nhóm số lượng theo medicineId (phòng trường hợp đơn có nhiều dòng cùng thuốc)
        Map<String, Integer> requestedQuantities = new HashMap<>();
        for (OrderCreatedItem item : event.items()) {
            requestedQuantities.merge(item.medicineId(), item.quantity(), Integer::sum);
        }

        // Bắt buộc sắp xếp danh sách medicineId theo thứ tự alphabet tăng dần trước khi truy vấn khóa dòng để chống deadlock
        List<String> sortedMedicineIds = requestedQuantities.keySet().stream().sorted().toList();

        LocalDate today = LocalDate.now();
        Map<String, List<Batch>> lockedBatchesMap = new HashMap<>();
        List<UnavailableItem> unavailableItems = new ArrayList<>();
        boolean isAllAvailable = true;

        // 3. Khóa dòng theo FEFO và kiểm tra tính khả dụng
        for (String medicineId : sortedMedicineIds) {
            int requestedQty = requestedQuantities.get(medicineId);
            List<Batch> batches = batchRepository.findAvailableBatchesForUpdate(medicineId, today);
            lockedBatchesMap.put(medicineId, batches);

            long totalAvailable = batches.stream().mapToLong(b -> b.quantityAvailable).sum();
            if (totalAvailable < requestedQty) {
                isAllAvailable = false;
                unavailableItems.add(new UnavailableItem(medicineId, requestedQty, totalAvailable));
            }
        }

        // 4. Phân nhánh All-or-Nothing
        if (!isAllAvailable) {
            // Thiếu bất kỳ mặt hàng nào thì KHÔNG giữ gì cả, phát event inventory.reservation-failed
            LOG.warnf("Đơn hàng orderId=%s thiếu hàng trong kho. Không giữ bất kỳ mặt hàng nào. Danh sách thiếu: %s",
                    event.orderId(), unavailableItems);

            InventoryReservationFailedEvent failedEvent = InventoryReservationFailedEvent.of(
                    event.correlationId(),
                    event.orderId(),
                    "OUT_OF_STOCK",
                    unavailableItems
            );

            createOutboxEvent(
                    "ORDER",
                    event.orderId().toString(),
                    "inventory.reservation-failed",
                    "inventory.reservation-failed",
                    event.orderId().toString(),
                    failedEvent
            );

            recordProcessedEvent(event.eventId(), "order.created", event.orderId(), "RESERVATION_FAILED");
            return;
        }

        // Đủ hàng: Trừ kho theo FEFO và tạo reservation
        List<ReservedItem> reservedItems = new ArrayList<>();
        for (String medicineId : sortedMedicineIds) {
            int remainingNeeded = requestedQuantities.get(medicineId);
            List<Batch> batches = lockedBatchesMap.get(medicineId);

            for (Batch batch : batches) {
                if (remainingNeeded <= 0) break;

                int alloc = Math.min(remainingNeeded, batch.quantityAvailable);
                batch.quantityAvailable -= alloc;
                batchRepository.persist(batch);

                // Tạo StockReservation
                StockReservation res = new StockReservation();
                res.orderId = event.orderId();
                res.medicineId = medicineId;
                res.batchId = batch.id;
                res.quantity = alloc;
                res.status = ReservationStatus.RESERVED;
                reservationRepository.persist(res);

                // Ghi sổ cái biến động kho RESERVE
                StockMovement movement = new StockMovement();
                movement.medicineId = medicineId;
                movement.batchId = batch.id;
                movement.type = MovementType.RESERVE;
                movement.quantity = alloc;
                movement.orderId = event.orderId();
                movement.reason = "Giữ hàng cho đơn hàng " + event.orderId();
                movement.createdBy = "SYSTEM";
                stockMovementRepository.persist(movement);

                remainingNeeded -= alloc;
            }

            reservedItems.add(new ReservedItem(medicineId, requestedQuantities.get(medicineId)));
        }

        // Tạo Outbox Event: inventory.reserved
        InventoryReservedEvent reservedEvent = InventoryReservedEvent.of(
                event.correlationId(),
                event.orderId(),
                reservedItems
        );

        createOutboxEvent(
                "ORDER",
                event.orderId().toString(),
                "inventory.reserved",
                "inventory.reserved",
                event.orderId().toString(),
                reservedEvent
        );

        recordProcessedEvent(event.eventId(), "order.created", event.orderId(), "RESERVED");
        LOG.infof("Giữ hàng FEFO thành công cho đơn hàng orderId=%s", event.orderId());
    }

    /**
     * Xử lý event order.status-changed:
     * - DA_HUY: Nhả các reservation đang RESERVED, hoàn lại tồn khả dụng cho các lô tương ứng.
     * - DANG_GIAO: Chuyển reservation sang COMMITTED (chốt trừ kho xuất giao).
     * - HOAN_TRA: Không tự nhập lại kho; ghi log và chờ Admin kiểm tra nhập thủ công.
     * - Xử lý idempotent: Đơn không có reservation thì bỏ qua và ghi log, không ném lỗi làm kẹt consumer.
     */
    @Transactional
    public void processOrderStatusChanged(OrderStatusChangedEvent event) {
        if (event == null || event.orderId() == null || event.newStatus() == null) {
            LOG.warn("Bỏ qua event order.status-changed không hợp lệ");
            return;
        }

        // Kiểm tra Idempotent theo eventId
        if (event.eventId() != null && processedEventRepository.findByEventId(event.eventId()).isPresent()) {
            LOG.infof("Event order.status-changed với eventId=%s đã được xử lý trước đó, bỏ qua", event.eventId());
            return;
        }

        String newStatus = event.newStatus();
        UUID orderId = event.orderId();

        if ("DA_HUY".equalsIgnoreCase(newStatus)) {
            List<StockReservation> reservations = reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED);
            if (reservations.isEmpty()) {
                LOG.infof("Đơn hàng orderId=%s bị hủy nhưng không có reservation nào đang RESERVED (có thể do đơn chưa giữ hàng hoặc đã hủy trước đó)", orderId);
                recordProcessedEvent(event.eventId(), "order.status-changed:DA_HUY", orderId, "IGNORED");
                return;
            }

            for (StockReservation res : reservations) {
                Optional<Batch> batchOpt = batchRepository.findByIdForUpdate(res.batchId);
                if (batchOpt.isPresent()) {
                    Batch batch = batchOpt.get();
                    batch.quantityAvailable += res.quantity;
                    batchRepository.persist(batch);
                }

                res.status = ReservationStatus.RELEASED;
                reservationRepository.persist(res);

                // Ghi sổ cái kho RELEASE
                StockMovement movement = new StockMovement();
                movement.medicineId = res.medicineId;
                movement.batchId = res.batchId;
                movement.type = MovementType.RELEASE;
                movement.quantity = res.quantity;
                movement.orderId = orderId;
                movement.reason = "Hoàn kho do đơn hàng bị hủy: " + (event.reason() != null ? event.reason() : "Không rõ lý do");
                movement.createdBy = "SYSTEM";
                stockMovementRepository.persist(movement);
            }

            recordProcessedEvent(event.eventId(), "order.status-changed:DA_HUY", orderId, "RELEASED");
            LOG.infof("Đã nhả hàng thành công cho đơn hàng bị hủy orderId=%s", orderId);

        } else if ("DANG_GIAO".equalsIgnoreCase(newStatus)) {
            List<StockReservation> reservations = reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED);
            if (reservations.isEmpty()) {
                LOG.infof("Đơn hàng orderId=%s chuyển DANG_GIAO nhưng không có reservation nào đang RESERVED", orderId);
                recordProcessedEvent(event.eventId(), "order.status-changed:DANG_GIAO", orderId, "IGNORED");
                return;
            }

            for (StockReservation res : reservations) {
                res.status = ReservationStatus.COMMITTED;
                reservationRepository.persist(res);

                // Ghi sổ cái kho EXPORT
                StockMovement movement = new StockMovement();
                movement.medicineId = res.medicineId;
                movement.batchId = res.batchId;
                movement.type = MovementType.EXPORT;
                movement.quantity = res.quantity;
                movement.orderId = orderId;
                movement.reason = "Xuất kho bàn giao đơn hàng cho bên vận chuyển";
                movement.createdBy = "SYSTEM";
                stockMovementRepository.persist(movement);
            }

            recordProcessedEvent(event.eventId(), "order.status-changed:DANG_GIAO", orderId, "COMMITTED");
            LOG.infof("Đã chốt trừ kho (COMMITTED) cho đơn hàng orderId=%s khi chuyển sang DANG_GIAO", orderId);

        } else if ("HOAN_TRA".equalsIgnoreCase(newStatus)) {
            LOG.infof("Đơn hàng orderId=%s chuyển sang HOAN_TRA. Không tự động nhập lại kho; Admin/Dược sĩ sẽ kiểm tra tình trạng thuốc và nhập lại thủ công nếu đạt chuẩn.", orderId);
            recordProcessedEvent(event.eventId(), "order.status-changed:HOAN_TRA", orderId, "IGNORED");

        } else {
            LOG.debugf("Trạng thái đơn hàng orderId=%s chuyển sang %s, kho không cần xử lý", orderId, newStatus);
            recordProcessedEvent(event.eventId(), "order.status-changed:" + newStatus, orderId, "IGNORED");
        }
    }

    private void createOutboxEvent(String aggType, String aggId, String eventType, String topic, String key, Object payloadObj) {
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
            LOG.errorf("Không thể chuyển đổi event sang JSON: %s", e.getMessage());
            throw new RuntimeException("Lỗi serialize event sang JSON", e);
        }
    }

    private void recordProcessedEvent(String eventId, String eventType, UUID orderId, String resultStatus) {
        ProcessedEvent pe = new ProcessedEvent();
        pe.eventId = eventId != null ? eventId : UUID.randomUUID().toString();
        pe.eventType = eventType;
        pe.orderId = orderId;
        pe.resultStatus = resultStatus;
        processedEventRepository.persist(pe);
    }
}

