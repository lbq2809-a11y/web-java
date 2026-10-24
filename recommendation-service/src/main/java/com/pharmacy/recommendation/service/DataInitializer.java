package com.pharmacy.recommendation.service;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.pharmacy.recommendation.dto.OrderCreatedEvent;
import com.pharmacy.recommendation.dto.OrderItemDto;
import com.pharmacy.recommendation.dto.OrderStatusChangedEvent;
import com.pharmacy.recommendation.repository.PendingOrderRepository;
import com.pharmacy.recommendation.repository.ProcessedEventRepository;
import com.pharmacy.recommendation.repository.ProductCooccurrenceRepository;
import com.pharmacy.recommendation.repository.ProductStatsRepository;
import com.pharmacy.recommendation.repository.UserPurchaseHistoryRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class DataInitializer {

    private static final Logger LOG = Logger.getLogger(DataInitializer.class);

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
    RecommendationService recommendationService;

    @ConfigProperty(name = "quarkus.profile", defaultValue = "prod")
    String profile;

    void onStart(@Observes StartupEvent ev) {
        try {
            ensureIndexes();
            if ("dev".equalsIgnoreCase(profile)) {
                seedDemoDataIfEmpty();
            }
        } catch (Exception e) {
            LOG.warnf("Không thể khởi tạo index hoặc nạp demo data (kiểm tra MongoDB): %s", e.getMessage());
        }
    }

    public void ensureIndexes() {
        // 1. pending_orders: unique orderId & TTL 30 ngày trên createdAt
        pendingOrderRepository.mongoCollection().createIndex(
                Indexes.ascending("orderId"),
                new IndexOptions().name("idx_pending_orders_order_id_unique").unique(true)
        );
        pendingOrderRepository.mongoCollection().createIndex(
                Indexes.ascending("createdAt"),
                new IndexOptions().name("idx_pending_orders_ttl").expireAfter(30L, TimeUnit.DAYS)
        );

        // 2. product_cooccurrence: unique (medicineId, coMedicineId) & query (medicineId, count DESC)
        cooccurrenceRepository.mongoCollection().createIndex(
                Indexes.ascending("medicineId", "coMedicineId"),
                new IndexOptions().name("idx_cooccurrence_pair_unique").unique(true)
        );
        cooccurrenceRepository.mongoCollection().createIndex(
                Indexes.compoundIndex(Indexes.ascending("medicineId"), Indexes.descending("count")),
                new IndexOptions().name("idx_cooccurrence_lookup")
        );

        // 3. product_stats: unique medicineId & query purchaseCount DESC
        productStatsRepository.mongoCollection().createIndex(
                Indexes.ascending("medicineId"),
                new IndexOptions().name("idx_product_stats_medicine_id_unique").unique(true)
        );
        productStatsRepository.mongoCollection().createIndex(
                Indexes.descending("purchaseCount"),
                new IndexOptions().name("idx_product_stats_ranking")
        );

        // 4. user_purchase_history: unique userId
        userPurchaseHistoryRepository.mongoCollection().createIndex(
                Indexes.ascending("userId"),
                new IndexOptions().name("idx_user_history_user_id_unique").unique(true)
        );

        // 5. processed_events: unique eventId
        processedEventRepository.mongoCollection().createIndex(
                Indexes.ascending("eventId"),
                new IndexOptions().name("idx_processed_events_event_id_unique").unique(true)
        );

        LOG.info("Khởi tạo toàn bộ chỉ mục (Indexes) MongoDB cho recommendation-service thành công.");
    }

    /**
     * Nạp sẵn ~30 đơn hàng giả lập với các cặp thuốc hay đi kèm nhau
     * để Swagger UI có dữ liệu xem ngay khi khởi động chế độ dev.
     */
    public void seedDemoDataIfEmpty() {
        if (cooccurrenceRepository.count() > 0) {
            LOG.info("Dữ liệu gợi ý đã tồn tại, bỏ qua bước seed demo data.");
            return;
        }

        LOG.info("Đang nạp ~30 đơn hàng giả lập để tạo dữ liệu gợi ý ban đầu...");

        // Danh sách các ID thuốc mẫu phổ biến (khớp với medicine-service)
        String panadolExtra = "6ad23be6c034632667baaa02"; // OTC
        String vitaminC = "6ad23be6c034632667baaa03";      // OTC
        String berberin = "6ad23be6c034632667baaa04";      // OTC
        String oresol = "6ad23be6c034632667baaa06";        // OTC
        String augmentin = "6ad23be6c034632667baaa05";     // ETC

        List<List<String>> sampleBaskets = List.of(
                List.of(panadolExtra, vitaminC),
                List.of(panadolExtra, vitaminC),
                List.of(panadolExtra, vitaminC),
                List.of(panadolExtra, vitaminC, augmentin),
                List.of(panadolExtra, vitaminC, augmentin),
                List.of(berberin, oresol),
                List.of(berberin, oresol),
                List.of(berberin, oresol),
                List.of(berberin, oresol, vitaminC),
                List.of(panadolExtra, berberin),
                List.of(panadolExtra, oresol),
                List.of(vitaminC, oresol),
                List.of(panadolExtra, vitaminC),
                List.of(panadolExtra, vitaminC),
                List.of(berberin, oresol),
                List.of(panadolExtra, vitaminC, oresol),
                List.of(panadolExtra, augmentin),
                List.of(vitaminC, augmentin),
                List.of(panadolExtra, vitaminC),
                List.of(berberin, oresol),
                List.of(panadolExtra, vitaminC),
                List.of(panadolExtra, vitaminC),
                List.of(berberin, oresol),
                List.of(berberin, oresol),
                List.of(panadolExtra, oresol),
                List.of(vitaminC, oresol),
                List.of(panadolExtra, vitaminC),
                List.of(berberin, oresol),
                List.of(panadolExtra, vitaminC),
                List.of(panadolExtra, vitaminC, berberin)
        );

        String sampleUserId = "550e8400-e29b-41d4-a716-446655440000";

        for (int i = 0; i < sampleBaskets.size(); i++) {
            String orderId = UUID.randomUUID().toString();
            String createdEventId = UUID.randomUUID().toString();
            String statusEventId = UUID.randomUUID().toString();
            List<String> items = sampleBaskets.get(i);

            List<OrderItemDto> itemDtos = items.stream()
                    .map(id -> new OrderItemDto(id, "Thuốc " + id, "OTC", BigDecimal.valueOf(50000), 1))
                    .toList();

            OrderCreatedEvent createdEvent = new OrderCreatedEvent(
                    createdEventId, "order.created", Instant.now(), "corr-seed",
                    orderId, sampleUserId, "test@pharmacy.local", "Test User",
                    null, "CHO_XU_LY", BigDecimal.valueOf(100000), itemDtos
            );
            recommendationService.handleOrderCreated(createdEvent);

            OrderStatusChangedEvent statusEvent = new OrderStatusChangedEvent(
                    statusEventId, "order.status-changed", Instant.now(), "corr-seed",
                    orderId, sampleUserId, "test@pharmacy.local", "Test User",
                    "CHO_XU_LY", "HOAN_THANH", "Hoàn thành đơn demo"
            );
            recommendationService.handleOrderStatusChanged(statusEvent);
        }

        LOG.infof("Đã nạp thành công %d đơn hàng mẫu vào recommendation_db.", sampleBaskets.size());
    }
}

