package com.pharmacy.recommendation.service;

import com.pharmacy.recommendation.client.MedicineServiceClient;
import com.pharmacy.recommendation.document.PendingOrderDocument;
import com.pharmacy.recommendation.document.ProductCooccurrenceDocument;
import com.pharmacy.recommendation.document.ProductStatsDocument;
import com.pharmacy.recommendation.document.UserPurchaseHistoryDocument;
import com.pharmacy.recommendation.dto.MedicineResponse;
import com.pharmacy.recommendation.dto.OrderCreatedEvent;
import com.pharmacy.recommendation.dto.OrderItemDto;
import com.pharmacy.recommendation.dto.OrderStatusChangedEvent;
import com.pharmacy.recommendation.dto.PagedResponse;
import com.pharmacy.recommendation.dto.RecommendationResponse;
import com.pharmacy.recommendation.exception.ApiException;
import com.pharmacy.recommendation.repository.PendingOrderRepository;
import com.pharmacy.recommendation.repository.ProductCooccurrenceRepository;
import com.pharmacy.recommendation.repository.ProductStatsRepository;
import com.pharmacy.recommendation.repository.ProcessedEventRepository;
import com.pharmacy.recommendation.repository.UserPurchaseHistoryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class RecommendationService {

    private static final Logger LOG = Logger.getLogger(RecommendationService.class);

    @Inject
    PendingOrderRepository pendingOrderRepository;

    @Inject
    ProductCooccurrenceRepository cooccurrenceRepository;

    @Inject
    ProductStatsRepository productStatsRepository;

    @Inject
    UserPurchaseHistoryRepository userPurchaseHistoryRepository;

    @Inject
    ProcessedEventRepository processedEventRepository;

    @Inject
    @RestClient
    MedicineServiceClient medicineServiceClient;

    /**
     * Xử lý event order.created:
     * Lưu bản ghi đơn hàng tạm vào collection pending_orders (orderId, userId, danh sách medicineId, createdAt).
     */
    public void handleOrderCreated(OrderCreatedEvent event) {
        if (event == null || event.orderId() == null) {
            LOG.warn("Bỏ qua event order.created do dữ liệu rỗng hoặc thiếu orderId");
            return;
        }

        if (event.eventId() != null && processedEventRepository.isEventProcessed(event.eventId())) {
            LOG.infof("Bỏ qua event order.created trùng lặp: eventId=%s, orderId=%s", event.eventId(), event.orderId());
            return;
        }

        if (pendingOrderRepository.findByOrderId(event.orderId()).isPresent()) {
            LOG.infof("Đơn hàng đã tồn tại trong pending_orders: orderId=%s", event.orderId());
            if (event.eventId() != null) {
                processedEventRepository.recordEvent(event.eventId(), event.eventType() != null ? event.eventType() : "order.created");
            }
            return;
        }

        PendingOrderDocument pendingOrder = new PendingOrderDocument();
        pendingOrder.orderId = event.orderId();
        pendingOrder.userId = event.customerId();
        if (event.items() != null) {
            pendingOrder.items = event.items().stream()
                    .map(OrderItemDto::medicineId)
                    .filter(id -> id != null && !id.isBlank())
                    .distinct()
                    .toList();
        } else {
            pendingOrder.items = List.of();
        }
        pendingOrder.processed = false;
        pendingOrder.createdAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();

        pendingOrderRepository.persist(pendingOrder);
        LOG.infof("Đã lưu đơn tạm pending_orders: orderId=%s, items=%s", pendingOrder.orderId, pendingOrder.items);

        if (event.eventId() != null) {
            processedEventRepository.recordEvent(event.eventId(), event.eventType() != null ? event.eventType() : "order.created");
        }
    }

    /**
     * Xử lý event order.status-changed:
     * - newStatus = HOAN_THANH: Cập nhật cooccurrence (2 chiều), product_stats (tăng purchaseCount), user_purchase_history ($addToSet).
     * - newStatus = DA_HUY: Xóa đơn tạm khỏi pending_orders.
     * - Khác: Bỏ qua.
     */
    public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
        if (event == null || event.orderId() == null) {
            LOG.warn("Bỏ qua event order.status-changed do dữ liệu rỗng hoặc thiếu orderId");
            return;
        }

        if (event.eventId() != null && processedEventRepository.isEventProcessed(event.eventId())) {
            LOG.infof("Bỏ qua event order.status-changed trùng lặp: eventId=%s, orderId=%s", event.eventId(), event.orderId());
            return;
        }

        String newStatus = event.newStatus();
        if ("HOAN_THANH".equalsIgnoreCase(newStatus)) {
            var pendingOpt = pendingOrderRepository.findByOrderId(event.orderId());
            if (pendingOpt.isPresent()) {
                PendingOrderDocument order = pendingOpt.get();
                if (!order.processed) {
                    List<String> items = order.items != null ? order.items : List.of();
                    LOG.infof("Đơn hàng hoàn thành, cập nhật thống kê cho orderId=%s, items=%s", order.orderId, items);

                    // 1. Cập nhật product_stats cho từng thuốc
                    for (String medId : items) {
                        productStatsRepository.incrementPurchaseCount(medId);
                    }

                    // 2. Cập nhật ma trận đồng xuất hiện 2 chiều A -> B và B -> A
                    for (int i = 0; i < items.size(); i++) {
                        for (int j = 0; j < items.size(); j++) {
                            if (i != j) {
                                cooccurrenceRepository.incrementCooccurrence(items.get(i), items.get(j));
                            }
                        }
                    }

                    // 3. Cập nhật lịch sử mua của user bằng toán tử $addToSet
                    if (order.userId != null && !order.userId.isBlank() && !items.isEmpty()) {
                        userPurchaseHistoryRepository.addPurchasedMedicines(order.userId, items);
                    }

                    // 4. Đánh dấu đơn đã được tính
                    order.processed = true;
                    pendingOrderRepository.update(order);
                } else {
                    LOG.infof("Đơn hàng %s đã được xử lý trước đó, bỏ qua tính lặp", order.orderId);
                }
            } else {
                LOG.warnf("Không tìm thấy đơn hàng tạm cho orderId=%s khi nhận HOAN_THANH", event.orderId());
            }
        } else if ("DA_HUY".equalsIgnoreCase(newStatus)) {
            LOG.infof("Đơn hàng bị hủy, xóa khỏi pending_orders: orderId=%s", event.orderId());
            pendingOrderRepository.deleteByOrderId(event.orderId());
        } else {
            LOG.debugf("Bỏ qua trạng thái order.status-changed không liên quan: orderId=%s, status=%s", event.orderId(), newStatus);
        }

        if (event.eventId() != null) {
            processedEventRepository.recordEvent(event.eventId(), event.eventType() != null ? event.eventType() : "order.status-changed");
        }
    }

    /**
     * API 1: Gợi ý thuốc hay mua kèm với medicineId (Frequently Bought Together)
     * - Sắp xếp count giảm dần, tie-break theo coMedicineId tăng dần.
     * - Thuốc ETC vẫn được phép hiển thị, có requiresPrescription = true.
     * - Loại bỏ thuốc active = false.
     */
    public List<RecommendationResponse> getAlsoBought(String medicineId, int limit) {
        validateLimit(limit);

        List<ProductCooccurrenceDocument> cooccurrences = cooccurrenceRepository.findTopCooccurring(medicineId, 50);
        if (cooccurrences.isEmpty()) {
            return List.of();
        }

        List<String> candidateIds = cooccurrences.stream()
                .map(doc -> doc.coMedicineId)
                .filter(id -> id != null && !id.isBlank() && !id.equals(medicineId))
                .distinct()
                .toList();

        if (candidateIds.isEmpty()) {
            return List.of();
        }

        try {
            String idsParam = String.join(",", candidateIds.stream().limit(50).toList());
            List<MedicineResponse> medicines = medicineServiceClient.getMedicinesBatch(idsParam);

            Map<String, MedicineResponse> medMap = medicines != null
                    ? medicines.stream().collect(Collectors.toMap(MedicineResponse::id, Function.identity(), (m1, m2) -> m1))
                    : Collections.emptyMap();

            List<RecommendationResponse> results = new ArrayList<>();
            for (String candidateId : candidateIds) {
                MedicineResponse med = medMap.get(candidateId);
                if (med != null && med.active()) {
                    boolean reqPrescription = "ETC".equalsIgnoreCase(med.prescriptionType());
                    results.add(new RecommendationResponse(
                            med.id(),
                            med.name(),
                            med.price(),
                            med.prescriptionType(),
                            reqPrescription,
                            "FREQUENTLY_BOUGHT_TOGETHER"
                    ));
                    if (results.size() >= limit) {
                        break;
                    }
                }
            }
            return results;
        } catch (Exception e) {
            LOG.warnf("Lỗi khi gọi medicine-service lấy chi tiết also-bought: %s. Chuyển sang fallback an toàn.", e.getMessage());
            // Fallback an toàn: Trả danh sách rút gọn chỉ gồm ID và reason, tuyệt đối không trả lỗi 500
            return candidateIds.stream()
                    .limit(limit)
                    .map(id -> new RecommendationResponse(
                            id,
                            "Thuốc " + id,
                            BigDecimal.ZERO,
                            "OTC",
                            false,
                            "FREQUENTLY_BOUGHT_TOGETHER"
                    ))
                    .toList();
        }
    }

    /**
     * API 2: Gợi ý cá nhân hóa cho người dùng (Personalized For Me)
     * - Lấy các thuốc khách đã từng mua từ user_purchase_history.
     * - Tìm các thuốc hay mua kèm trong product_cooccurrence, loại bỏ các thuốc khách đã từng mua.
     * - AN TOÀN TUYỆT ĐỐI: Không bao giờ gợi ý thuốc kê đơn (ETC); chỉ giữ lại thuốc OTC và active = true.
     * - Fallback sang Best Sellers từ product_stats nếu khách hàng mới hoặc sau khi lọc bị rỗng.
     */
    public List<RecommendationResponse> getForMe(String userId, int limit) {
        validateLimit(limit);

        var historyOpt = userPurchaseHistoryRepository.findByUserId(userId);
        List<String> purchasedIds = historyOpt.map(doc -> doc.purchasedMedicineIds).orElse(List.of());
        Set<String> purchasedSet = new HashSet<>(purchasedIds != null ? purchasedIds : List.of());

        List<RecommendationResponse> personalizedList = new ArrayList<>();

        if (!purchasedSet.isEmpty()) {
            List<ProductCooccurrenceDocument> coDocs = cooccurrenceRepository.findCooccurringForMedicines(new ArrayList<>(purchasedSet));
            // Tổng hợp điểm co-occurrence cho các thuốc ứng viên
            Map<String, Long> scoreMap = new HashMap<>();
            for (ProductCooccurrenceDocument doc : coDocs) {
                if (doc.coMedicineId != null && !purchasedSet.contains(doc.coMedicineId)) {
                    scoreMap.merge(doc.coMedicineId, doc.count, Long::sum);
                }
            }

            // Sắp xếp ứng viên theo điểm giảm dần, tie-break theo ID tăng dần
            List<String> sortedCandidates = scoreMap.entrySet().stream()
                    .sorted((e1, e2) -> {
                        int cmp = Long.compare(e2.getValue(), e1.getValue());
                        return cmp != 0 ? cmp : e1.getKey().compareTo(e2.getKey());
                    })
                    .map(Map.Entry::getKey)
                    .limit(50)
                    .toList();

            if (!sortedCandidates.isEmpty()) {
                try {
                    String idsParam = String.join(",", sortedCandidates);
                    List<MedicineResponse> medicines = medicineServiceClient.getMedicinesBatch(idsParam);
                    if (medicines != null) {
                        Map<String, MedicineResponse> medMap = medicines.stream()
                                .collect(Collectors.toMap(MedicineResponse::id, Function.identity(), (m1, m2) -> m1));

                        for (String candId : sortedCandidates) {
                            MedicineResponse med = medMap.get(candId);
                            // NGUYÊN TẮC AN TOÀN: TUYỆT ĐỐI KHÔNG GỢI Ý THUỐC ETC, CHỈ LẤY OTC ACTIVE
                            if (med != null && med.active() && !"ETC".equalsIgnoreCase(med.prescriptionType())) {
                                personalizedList.add(new RecommendationResponse(
                                        med.id(),
                                        med.name(),
                                        med.price(),
                                        "OTC",
                                        false,
                                        "FOR_YOU"
                                ));
                                if (personalizedList.size() >= limit) {
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.warnf("Lỗi khi tra cứu chi tiết thuốc cho gợi ý for-me: %s", e.getMessage());
                }
            }
        }

        // Nếu có kết quả cá nhân hóa hợp lệ -> trả về
        if (!personalizedList.isEmpty()) {
            return personalizedList;
        }

        // FALLBACK: Người dùng mới hoặc danh sách lọc rỗng -> Lấy Top thuốc bán chạy nhất (Best Sellers)
        LOG.infof("Áp dụng fallback Top bán chạy (Best Sellers) cho userId=%s", userId);
        return getBestSellersFallback(purchasedSet, limit);
    }

    private List<RecommendationResponse> getBestSellersFallback(Set<String> excludedIds, int limit) {
        List<ProductStatsDocument> topSelling = productStatsRepository.findTopSelling(50);
        List<String> candidateIds = topSelling.stream()
                .map(doc -> doc.medicineId)
                .filter(id -> id != null && !id.isBlank() && !excludedIds.contains(id))
                .distinct()
                .limit(50)
                .toList();

        if (candidateIds.isEmpty()) {
            return List.of();
        }

        try {
            String idsParam = String.join(",", candidateIds);
            List<MedicineResponse> medicines = medicineServiceClient.getMedicinesBatch(idsParam);
            if (medicines == null || medicines.isEmpty()) {
                return List.of();
            }

            Map<String, MedicineResponse> medMap = medicines.stream()
                    .collect(Collectors.toMap(MedicineResponse::id, Function.identity(), (m1, m2) -> m1));

            List<RecommendationResponse> fallbackList = new ArrayList<>();
            for (String candId : candidateIds) {
                MedicineResponse med = medMap.get(candId);
                // NGUYÊN TẮC AN TOÀN: CHỈ LẤY OTC ACTIVE
                if (med != null && med.active() && !"ETC".equalsIgnoreCase(med.prescriptionType())) {
                    fallbackList.add(new RecommendationResponse(
                            med.id(),
                            med.name(),
                            med.price(),
                            "OTC",
                            false,
                            "BEST_SELLER"
                    ));
                    if (fallbackList.size() >= limit) {
                        break;
                    }
                }
            }
            return fallbackList;
        } catch (Exception e) {
            LOG.warnf("Lỗi khi tra cứu danh sách Best Sellers từ medicine-service: %s", e.getMessage());
            return candidateIds.stream()
                    .limit(limit)
                    .map(id -> new RecommendationResponse(
                            id,
                            "Thuốc " + id,
                            BigDecimal.ZERO,
                            "OTC",
                            false,
                            "BEST_SELLER"
                    ))
                    .toList();
        }
    }

    /**
     * API 3: Gợi ý các thuốc cùng danh mục (Related by Category)
     * - Gọi medicine-service lấy categoryId của thuốc truyền vào.
     * - Lấy các thuốc cùng danh mục, loại trừ chính nó.
     * - Giữ cả OTC và ETC (ETC có requiresPrescription = true).
     */
    public List<RecommendationResponse> getRelated(String medicineId, int limit) {
        validateLimit(limit);

        try {
            MedicineResponse currentMedicine = medicineServiceClient.getMedicineById(medicineId);
            if (currentMedicine == null || currentMedicine.categoryId() == null) {
                return List.of();
            }

            PagedResponse<MedicineResponse> pagedResponse = medicineServiceClient.getMedicinesByCategory(
                    currentMedicine.categoryId(), 0, 50
            );

            if (pagedResponse == null || pagedResponse.content() == null) {
                return List.of();
            }

            List<RecommendationResponse> results = new ArrayList<>();
            for (MedicineResponse med : pagedResponse.content()) {
                if (med != null && med.active() && !medicineId.equals(med.id())) {
                    boolean reqPrescription = "ETC".equalsIgnoreCase(med.prescriptionType());
                    results.add(new RecommendationResponse(
                            med.id(),
                            med.name(),
                            med.price(),
                            med.prescriptionType(),
                            reqPrescription,
                            "SAME_CATEGORY"
                    ));
                    if (results.size() >= limit) {
                        break;
                    }
                }
            }
            return results;
        } catch (Exception e) {
            LOG.warnf("Lỗi khi lấy thuốc cùng danh mục từ medicine-service: %s. Trả về rỗng an toàn.", e.getMessage());
            return List.of();
        }
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > 20) {
            throw new ApiException("VALIDATION_ERROR", "Tham số limit phải từ 1 đến 20", 400, List.of("limit: giá trị phải nằm trong khoảng từ 1 đến 20"));
        }
    }
}

