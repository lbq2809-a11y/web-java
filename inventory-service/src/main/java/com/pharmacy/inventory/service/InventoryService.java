package com.pharmacy.inventory.service;

import com.pharmacy.inventory.client.MedicineResponse;
import com.pharmacy.inventory.client.MedicineServiceClient;
import com.pharmacy.inventory.dto.BatchResponse;
import com.pharmacy.inventory.dto.CreateBatchRequest;
import com.pharmacy.inventory.dto.LowStockAlertDto;
import com.pharmacy.inventory.dto.NearExpiryAlertDto;
import com.pharmacy.inventory.dto.PagedResponse;
import com.pharmacy.inventory.dto.StockAdjustmentRequest;
import com.pharmacy.inventory.dto.StockAlertsResponse;
import com.pharmacy.inventory.dto.StockCheckItem;
import com.pharmacy.inventory.dto.StockCheckItemResult;
import com.pharmacy.inventory.dto.StockCheckRequest;
import com.pharmacy.inventory.dto.StockCheckResponse;
import com.pharmacy.inventory.dto.StockMovementResponse;
import com.pharmacy.inventory.dto.StockResponse;
import com.pharmacy.inventory.dto.StockSummaryResponse;
import com.pharmacy.inventory.entity.Batch;
import com.pharmacy.inventory.entity.MovementType;
import com.pharmacy.inventory.entity.StockMovement;
import com.pharmacy.inventory.exception.ApiException;
import com.pharmacy.inventory.mapper.BatchMapper;
import com.pharmacy.inventory.mapper.StockMovementMapper;
import com.pharmacy.inventory.repository.BatchRepository;
import com.pharmacy.inventory.repository.StockMovementRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class InventoryService {

    private static final Logger LOG = Logger.getLogger(InventoryService.class);

    @Inject
    BatchRepository batchRepository;

    @Inject
    StockMovementRepository stockMovementRepository;

    @Inject
    @RestClient
    MedicineServiceClient medicineServiceClient;

    @ConfigProperty(name = "inventory.alert.near-expiry-days", defaultValue = "90")
    int nearExpiryDaysConfig;

    @ConfigProperty(name = "inventory.alert.low-stock-threshold", defaultValue = "20")
    int lowStockThresholdConfig;

    @Transactional
    public BatchResponse createBatch(CreateBatchRequest req, String createdBy) {
        if (req.expiryDate().isBefore(LocalDate.now()) || req.expiryDate().isEqual(LocalDate.now())) {
            throw ApiException.badRequest("INVALID_EXPIRY_DATE", "Ngày hết hạn của lô mới nhập phải ở tương lai");
        }

        if (batchRepository.findByMedicineIdAndBatchNumber(req.medicineId(), req.batchNumber()).isPresent()) {
            throw ApiException.conflict("BATCH_ALREADY_EXISTS",
                    "Số lô '" + req.batchNumber() + "' của thuốc này đã tồn tại trong hệ thống");
        }

        Batch batch = new Batch();
        batch.medicineId = req.medicineId();
        batch.batchNumber = req.batchNumber();
        batch.expiryDate = req.expiryDate();
        batch.quantityIn = req.quantity();
        batch.quantityAvailable = req.quantity();
        batch.costPrice = req.costPrice();
        batchRepository.persist(batch);

        // Ghi sổ cái biến động kho: Nhập lô
        StockMovement movement = new StockMovement();
        movement.medicineId = batch.medicineId;
        movement.batchId = batch.id;
        movement.type = MovementType.IMPORT;
        movement.quantity = batch.quantityIn;
        movement.reason = "Nhập lô thuốc mới: " + batch.batchNumber;
        movement.createdBy = createdBy != null ? createdBy : "SYSTEM";
        stockMovementRepository.persist(movement);

        LOG.infof("Đã nhập lô thành công: batchId=%s, medicineId=%s, quantity=%d",
                batch.id, batch.medicineId, batch.quantityIn);

        return BatchMapper.toResponse(batch);
    }

    public BatchResponse getBatchById(UUID id) {
        Batch batch = batchRepository.findByIdOptional(id)
                .orElseThrow(() -> ApiException.notFound("BATCH_NOT_FOUND", "Không tìm thấy lô thuốc với ID: " + id));
        return BatchMapper.toResponse(batch);
    }

    public PagedResponse<BatchResponse> searchBatches(String medicineId, Integer nearExpiryDays, int page, int size) {
        List<Batch> batches = batchRepository.searchBatches(medicineId, nearExpiryDays, page, size);
        long total = batchRepository.countSearchBatches(medicineId, nearExpiryDays);
        List<BatchResponse> list = batches.stream().map(BatchMapper::toResponse).toList();
        return PagedResponse.of(list, page, size, total);
    }

    public StockResponse getStockByMedicineId(String medicineId) {
        List<Batch> batches = batchRepository.findByMedicineId(medicineId);
        long totalAvailable = batchRepository.sumAvailableQuantity(medicineId, LocalDate.now());
        List<BatchResponse> batchResponses = batches.stream().map(BatchMapper::toResponse).toList();
        return new StockResponse(medicineId, totalAvailable, batchResponses);
    }

    public PagedResponse<StockSummaryResponse> getStockSummary(int page, int size) {
        // Lấy danh sách distinct medicineId
        List<String> medicineIds = batchRepository.find("select distinct b.medicineId from Batch b")
                .project(String.class)
                .page(io.quarkus.panache.common.Page.of(page, size))
                .list();

        long totalElements = batchRepository.find("select count(distinct b.medicineId) from Batch b")
                .project(Long.class)
                .firstResult();

        LocalDate today = LocalDate.now();
        List<StockSummaryResponse> summaries = new ArrayList<>();
        for (String medId : medicineIds) {
            long totalAvail = batchRepository.sumAvailableQuantity(medId, today);
            long totalBatches = batchRepository.count("medicineId = ?1", medId);
            summaries.add(new StockSummaryResponse(medId, totalAvail, (int) totalBatches));
        }

        return PagedResponse.of(summaries, page, size, totalElements);
    }

    @Transactional
    public BatchResponse adjustStock(StockAdjustmentRequest req, String createdBy) {
        Batch batch = batchRepository.findByIdForUpdate(req.batchId())
                .orElseThrow(() -> ApiException.notFound("BATCH_NOT_FOUND", "Không tìm thấy lô thuốc với ID: " + req.batchId()));

        int newQuantity = batch.quantityAvailable + req.adjustmentQuantity();
        if (newQuantity < 0) {
            throw ApiException.badRequest("INVALID_STOCK_ADJUSTMENT",
                    "Số lượng tồn sau điều chỉnh không thể âm (Hiện tại: " + batch.quantityAvailable +
                            ", Điều chỉnh: " + req.adjustmentQuantity() + ")");
        }

        batch.quantityAvailable = newQuantity;
        batchRepository.persist(batch);

        // Ghi sổ cái biến động kho: Điều chỉnh
        StockMovement movement = new StockMovement();
        movement.medicineId = batch.medicineId;
        movement.batchId = batch.id;
        movement.type = MovementType.ADJUST;
        movement.quantity = req.adjustmentQuantity();
        movement.reason = req.reason();
        movement.createdBy = createdBy != null ? createdBy : "SYSTEM";
        stockMovementRepository.persist(movement);

        LOG.infof("Đã điều chỉnh tồn lô %s: thay đổi %d, tồn mới %d, lý do: %s",
                batch.id, req.adjustmentQuantity(), newQuantity, req.reason());

        return BatchMapper.toResponse(batch);
    }

    public PagedResponse<StockMovementResponse> searchMovements(String medicineId, UUID batchId, MovementType type, int page, int size) {
        List<StockMovement> movements = stockMovementRepository.searchMovements(medicineId, batchId, type, page, size);
        long total = stockMovementRepository.countSearchMovements(medicineId, batchId, type);
        List<StockMovementResponse> list = movements.stream().map(StockMovementMapper::toResponse).toList();
        return PagedResponse.of(list, page, size, total);
    }

    public StockCheckResponse checkStock(StockCheckRequest req) {
        LocalDate today = LocalDate.now();
        List<StockCheckItemResult> results = new ArrayList<>();
        boolean allAvailable = true;

        for (StockCheckItem item : req.items()) {
            long available = batchRepository.sumAvailableQuantity(item.medicineId(), today);
            boolean sufficient = available >= item.quantity();
            if (!sufficient) {
                allAvailable = false;
            }
            results.add(new StockCheckItemResult(item.medicineId(), item.quantity(), available, sufficient));
        }

        return new StockCheckResponse(allAvailable, results);
    }

    public StockAlertsResponse getActiveAlerts() {
        LocalDate today = LocalDate.now();
        LocalDate targetDate = today.plusDays(nearExpiryDaysConfig);

        // 1. Quét lô sắp hết hạn
        List<Batch> nearExpiryBatches = batchRepository.findNearExpiryBatches(today, targetDate);

        // 2. Quét thuốc dưới ngưỡng tồn
        List<String> allMedicineIds = batchRepository.find("select distinct b.medicineId from Batch b")
                .project(String.class)
                .list();

        Map<String, Long> lowStockMap = new HashMap<>();
        for (String medId : allMedicineIds) {
            long avail = batchRepository.sumAvailableQuantity(medId, today);
            if (avail < lowStockThresholdConfig) {
                lowStockMap.put(medId, avail);
            }
        }

        // 3. Tra cứu tên thuốc từ medicine-service
        List<String> needNames = new ArrayList<>(lowStockMap.keySet());
        nearExpiryBatches.forEach(b -> {
            if (!needNames.contains(b.medicineId)) needNames.add(b.medicineId);
        });

        Map<String, String> namesMap = fetchMedicineNames(needNames);

        List<NearExpiryAlertDto> nearExpiryAlerts = nearExpiryBatches.stream().map(b -> {
            long daysLeft = ChronoUnit.DAYS.between(today, b.expiryDate);
            return new NearExpiryAlertDto(
                    b.id,
                    b.medicineId,
                    namesMap.get(b.medicineId),
                    b.batchNumber,
                    b.expiryDate,
                    b.quantityAvailable,
                    daysLeft
            );
        }).toList();

        List<LowStockAlertDto> lowStockAlerts = lowStockMap.entrySet().stream().map(entry ->
                new LowStockAlertDto(
                        entry.getKey(),
                        namesMap.get(entry.getKey()),
                        entry.getValue(),
                        lowStockThresholdConfig
                )
        ).toList();

        return new StockAlertsResponse(nearExpiryAlerts, lowStockAlerts);
    }

    public Map<String, String> fetchMedicineNames(List<String> medicineIds) {
        if (medicineIds == null || medicineIds.isEmpty()) return Collections.emptyMap();
        try {
            String idsParam = String.join(",", medicineIds);
            List<MedicineResponse> list = medicineServiceClient.getMedicinesBatch(idsParam);
            if (list != null) {
                return list.stream()
                        .filter(m -> m.id != null && m.name != null)
                        .collect(Collectors.toMap(m -> m.id, m -> m.name, (a, b) -> a));
            }
        } catch (Exception e) {
            LOG.warnf("Không thể lấy tên thuốc từ medicine-service: %s", e.getMessage());
        }
        return Collections.emptyMap();
    }
}

