package com.pharmacy.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.recommendation.client.MedicineServiceClient;
import com.pharmacy.recommendation.dto.MedicineResponse;
import com.pharmacy.recommendation.dto.OrderCreatedEvent;
import com.pharmacy.recommendation.dto.OrderItemDto;
import com.pharmacy.recommendation.dto.OrderStatusChangedEvent;
import com.pharmacy.recommendation.dto.PagedResponse;
import com.pharmacy.recommendation.dto.RecommendationResponse;
import com.pharmacy.recommendation.document.PendingOrderDocument;
import com.pharmacy.recommendation.document.ProductCooccurrenceDocument;
import com.pharmacy.recommendation.document.ProductStatsDocument;
import com.pharmacy.recommendation.document.UserPurchaseHistoryDocument;
import com.pharmacy.recommendation.repository.PendingOrderRepository;
import com.pharmacy.recommendation.repository.ProcessedEventRepository;
import com.pharmacy.recommendation.repository.ProductCooccurrenceRepository;
import com.pharmacy.recommendation.repository.ProductStatsRepository;
import com.pharmacy.recommendation.repository.UserPurchaseHistoryRepository;
import com.pharmacy.recommendation.service.RecommendationConsumer;
import com.pharmacy.recommendation.service.RecommendationService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RecommendationResourceTest {

    @InjectMock
    @RestClient
    MedicineServiceClient medicineServiceClient;

    @Inject
    RecommendationService recommendationService;

    @Inject
    RecommendationConsumer recommendationConsumer;

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
    ObjectMapper objectMapper;

    @Inject
    @Any
    InMemoryConnector connector;

    private static final String MED_PANADOL = "6ad23be6c034632667baaa02"; // OTC
    private static final String MED_VITAMIN_C = "6ad23be6c034632667baaa03"; // OTC
    private static final String MED_BERBERIN = "6ad23be6c034632667baaa04"; // OTC
    private static final String MED_AUGMENTIN = "6ad23be6c034632667baaa05"; // ETC
    private static final String MED_INACTIVE = "6ad23be6c034632667baaa99"; // OTC inactive

    @BeforeEach
    void setUp() {
        Mockito.reset(medicineServiceClient);
        pendingOrderRepository.deleteAll();
        cooccurrenceRepository.deleteAll();
        productStatsRepository.deleteAll();
        userPurchaseHistoryRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    private String generateToken(String userId, String role) {
        return Jwt.issuer("https://pharmacy.com/issuer")
                .subject(userId)
                .upn(role.toLowerCase() + "@pharmacy.local")
                .groups(Set.of(role))
                .claim("role", role)
                .expiresIn(Duration.ofHours(2))
                .sign();
    }

    @Test
    @Order(1)
    @DisplayName("1. Event order.created lưu bản ghi đơn tạm vào collection pending_orders")
    void testOrderCreatedSavesPendingOrder() {
        String orderId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();

        OrderCreatedEvent event = new OrderCreatedEvent(
                eventId, "order.created", Instant.now(), "corr-1",
                orderId, userId, "user@test.local", "Nguyen Van A",
                null, "CHO_XU_LY", BigDecimal.valueOf(100000),
                List.of(
                        new OrderItemDto(MED_PANADOL, "Panadol", "OTC", BigDecimal.valueOf(55000), 1),
                        new OrderItemDto(MED_VITAMIN_C, "Vitamin C", "OTC", BigDecimal.valueOf(45000), 1)
                )
        );

        recommendationConsumer.consumeOrderCreated(assertDoesNotThrow(() -> objectMapper.writeValueAsString(event)));

        var pendingOpt = pendingOrderRepository.findByOrderId(orderId);
        assertTrue(pendingOpt.isPresent());
        assertEquals(userId, pendingOpt.get().userId);
        assertFalse(pendingOpt.get().processed);
        assertEquals(2, pendingOpt.get().items.size());
        assertTrue(pendingOpt.get().items.contains(MED_PANADOL));
        assertTrue(pendingOpt.get().items.contains(MED_VITAMIN_C));
        assertTrue(processedEventRepository.isEventProcessed(eventId));
    }

    @Test
    @Order(2)
    @DisplayName("2. Event order.status-changed (HOAN_THANH) cập nhật cooccurrence 2 chiều, stats và purchase history")
    void testOrderStatusChangedCompletedUpdatesStats() {
        String orderId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        // 1. Tạo đơn tạm
        OrderCreatedEvent createEv = new OrderCreatedEvent(
                UUID.randomUUID().toString(), "order.created", Instant.now(), "corr-2",
                orderId, userId, "user@test.local", "Nguyen Van A",
                null, "CHO_XU_LY", BigDecimal.valueOf(100000),
                List.of(
                        new OrderItemDto(MED_PANADOL, "Panadol", "OTC", BigDecimal.valueOf(55000), 1),
                        new OrderItemDto(MED_VITAMIN_C, "Vitamin C", "OTC", BigDecimal.valueOf(45000), 1)
                )
        );
        recommendationService.handleOrderCreated(createEv);

        // 2. Chuyển sang hoàn thành
        String statusEventId = UUID.randomUUID().toString();
        OrderStatusChangedEvent statusEv = new OrderStatusChangedEvent(
                statusEventId, "order.status-changed", Instant.now(), "corr-2",
                orderId, userId, "user@test.local", "Nguyen Van A",
                "DANG_GIAO", "HOAN_THANH", "Giao hàng thành công"
        );
        recommendationConsumer.consumeOrderStatusChanged(assertDoesNotThrow(() -> objectMapper.writeValueAsString(statusEv)));

        // Kiểm tra đơn tạm đã processed = true
        var pendingOpt = pendingOrderRepository.findByOrderId(orderId);
        assertTrue(pendingOpt.isPresent());
        assertTrue(pendingOpt.get().processed);

        // Kiểm tra co-occurrence 2 chiều: Panadol -> Vit C và Vit C -> Panadol
        var topWithPanadol = cooccurrenceRepository.findTopCooccurring(MED_PANADOL, 10);
        assertEquals(1, topWithPanadol.size());
        assertEquals(MED_VITAMIN_C, topWithPanadol.get(0).coMedicineId);
        assertEquals(1, topWithPanadol.get(0).count);

        var topWithVitC = cooccurrenceRepository.findTopCooccurring(MED_VITAMIN_C, 10);
        assertEquals(1, topWithVitC.size());
        assertEquals(MED_PANADOL, topWithVitC.get(0).coMedicineId);
        assertEquals(1, topWithVitC.get(0).count);

        // Kiểm tra product_stats
        var statsPanadol = productStatsRepository.find("medicineId", MED_PANADOL).firstResult();
        assertNotNull(statsPanadol);
        assertEquals(1, statsPanadol.purchaseCount);

        // Kiểm tra user_purchase_history
        var historyOpt = userPurchaseHistoryRepository.findByUserId(userId);
        assertTrue(historyOpt.isPresent());
        assertTrue(historyOpt.get().purchasedMedicineIds.contains(MED_PANADOL));
        assertTrue(historyOpt.get().purchasedMedicineIds.contains(MED_VITAMIN_C));
    }

    @Test
    @Order(3)
    @DisplayName("3. Event order.status-changed (DA_HUY) xóa đơn tạm khỏi pending_orders")
    void testOrderStatusChangedCancelledDeletesPendingOrder() {
        String orderId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        OrderCreatedEvent createEv = new OrderCreatedEvent(
                UUID.randomUUID().toString(), "order.created", Instant.now(), "corr-3",
                orderId, userId, "user@test.local", "Nguyen Van A",
                null, "CHO_XU_LY", BigDecimal.valueOf(100000),
                List.of(new OrderItemDto(MED_PANADOL, "Panadol", "OTC", BigDecimal.valueOf(55000), 1))
        );
        recommendationService.handleOrderCreated(createEv);
        assertTrue(pendingOrderRepository.findByOrderId(orderId).isPresent());

        // Gửi event DA_HUY
        OrderStatusChangedEvent cancelEv = new OrderStatusChangedEvent(
                UUID.randomUUID().toString(), "order.status-changed", Instant.now(), "corr-3",
                orderId, userId, "user@test.local", "Nguyen Van A",
                "CHO_XU_LY", "DA_HUY", "Khách hàng hủy đơn"
        );
        recommendationService.handleOrderStatusChanged(cancelEv);

        // Đơn tạm phải bị xóa
        assertTrue(pendingOrderRepository.findByOrderId(orderId).isEmpty());
        // Thống kê không bị tăng
        assertEquals(0, productStatsRepository.count());
    }

    @Test
    @Order(4)
    @DisplayName("4. Event order.status-changed (trạng thái khác: CHO_XU_LY, DANG_GIAO) bị bỏ qua không tính thống kê")
    void testOrderStatusChangedOtherStatusIgnored() {
        String orderId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        OrderCreatedEvent createEv = new OrderCreatedEvent(
                UUID.randomUUID().toString(), "order.created", Instant.now(), "corr-4",
                orderId, userId, "user@test.local", "Nguyen Van A",
                null, "CHO_XU_LY", BigDecimal.valueOf(100000),
                List.of(new OrderItemDto(MED_PANADOL, "Panadol", "OTC", BigDecimal.valueOf(55000), 1))
        );
        recommendationService.handleOrderCreated(createEv);

        OrderStatusChangedEvent shippingEv = new OrderStatusChangedEvent(
                UUID.randomUUID().toString(), "order.status-changed", Instant.now(), "corr-4",
                orderId, userId, "user@test.local", "Nguyen Van A",
                "CHO_XU_LY", "DANG_GIAO", "Đã bàn giao vận chuyển"
        );
        recommendationService.handleOrderStatusChanged(shippingEv);

        var pending = pendingOrderRepository.findByOrderId(orderId).orElseThrow();
        assertFalse(pending.processed);
        assertEquals(0, productStatsRepository.count());
        assertEquals(0, cooccurrenceRepository.count());
    }

    @Test
    @Order(5)
    @DisplayName("5. Idempotent: Event Kafka trùng lặp không bị cộng dồn hai lần")
    void testIdempotencyDuplicateEvents() {
        String orderId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String statusEventId = UUID.randomUUID().toString();

        OrderCreatedEvent createEv = new OrderCreatedEvent(
                UUID.randomUUID().toString(), "order.created", Instant.now(), "corr-5",
                orderId, userId, "user@test.local", "Nguyen Van A",
                null, "CHO_XU_LY", BigDecimal.valueOf(100000),
                List.of(
                        new OrderItemDto(MED_PANADOL, "Panadol", "OTC", BigDecimal.valueOf(55000), 1),
                        new OrderItemDto(MED_VITAMIN_C, "Vitamin C", "OTC", BigDecimal.valueOf(45000), 1)
                )
        );
        recommendationService.handleOrderCreated(createEv);

        OrderStatusChangedEvent statusEv = new OrderStatusChangedEvent(
                statusEventId, "order.status-changed", Instant.now(), "corr-5",
                orderId, userId, "user@test.local", "Nguyen Van A",
                "DANG_GIAO", "HOAN_THANH", "Giao thành công"
        );

        // Gửi lần 1
        recommendationService.handleOrderStatusChanged(statusEv);
        // Gửi lại lần 2 (cùng eventId)
        recommendationService.handleOrderStatusChanged(statusEv);

        // Kiểm tra count chỉ bằng 1
        var list = cooccurrenceRepository.findTopCooccurring(MED_PANADOL, 10);
        assertEquals(1, list.size());
        assertEquals(1, list.get(0).count);

        var stats = productStatsRepository.find("medicineId", MED_PANADOL).firstResult();
        assertEquals(1, stats.purchaseCount);
    }

    @Test
    @Order(6)
    @DisplayName("6. Consumer Kafka chịu lỗi: JSON hỏng không làm crash tiến trình")
    void testMalformedJsonDoesNotCrashConsumer() {
        assertDoesNotThrow(() -> recommendationConsumer.consumeOrderCreated("invalid json {[{"));
        assertDoesNotThrow(() -> recommendationConsumer.consumeOrderStatusChanged("invalid json {[{"));

        // Gửi qua in-memory channel cũng an toàn
        InMemorySource<String> createdSource = connector.source("order-created-in");
        assertDoesNotThrow(() -> createdSource.send("malformed-payload-123"));
    }

    @Test
    @Order(7)
    @DisplayName("7. GET /also-bought: Đúng thứ tự count giảm dần, tie-break ID tăng dần, đúng requiresPrescription")
    void testAlsoBoughtOrderingAndTieBreak() {
        // Tạo dữ liệu đồng xuất hiện với Panadol:
        // Berberin: 5 lần
        // Vitamin C: 10 lần
        // Augmentin (ETC): 10 lần (cùng count với Vit C để test tie-break)
        for (int i = 0; i < 5; i++) cooccurrenceRepository.incrementCooccurrence(MED_PANADOL, MED_BERBERIN);
        for (int i = 0; i < 10; i++) cooccurrenceRepository.incrementCooccurrence(MED_PANADOL, MED_VITAMIN_C);
        for (int i = 0; i < 10; i++) cooccurrenceRepository.incrementCooccurrence(MED_PANADOL, MED_AUGMENTIN);

        // Mock medicine-service
        MedicineResponse resVitC = new MedicineResponse(
                MED_VITAMIN_C, "MED-003", "Vitamin C", "Vit C", "DHG", "Hộp",
                BigDecimal.valueOf(45000), null, null, null, null, "OTC", "cat-1", "Vitamin", true, 5.0, 1, Instant.now(), Instant.now()
        );
        MedicineResponse resAugmentin = new MedicineResponse(
                MED_AUGMENTIN, "MED-005", "Augmentin 1g", "Amox", "GSK", "Hộp",
                BigDecimal.valueOf(285000), null, null, null, null, "ETC", "cat-2", "Kháng sinh", true, 5.0, 1, Instant.now(), Instant.now()
        );
        MedicineResponse resBerberin = new MedicineResponse(
                MED_BERBERIN, "MED-004", "Berberin", "Berberin", "OPC", "Lọ",
                BigDecimal.valueOf(35000), null, null, null, null, "OTC", "cat-3", "Tiêu hóa", true, 5.0, 1, Instant.now(), Instant.now()
        );

        when(medicineServiceClient.getMedicinesBatch(anyString())).thenReturn(List.of(resVitC, resAugmentin, resBerberin));

        given()
                .when()
                .get("/api/v1/recommendations/also-bought/" + MED_PANADOL + "?limit=5")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(3))
                // Tie-break giữa MED_VITAMIN_C (...03) và MED_AUGMENTIN (...05) khi cùng count = 10 -> ID ...03 trước
                .body("[0].medicineId", equalTo(MED_VITAMIN_C))
                .body("[0].requiresPrescription", equalTo(false))
                .body("[0].reason", equalTo("FREQUENTLY_BOUGHT_TOGETHER"))
                // Augmentin (ETC) được trả về kèm requiresPrescription = true
                .body("[1].medicineId", equalTo(MED_AUGMENTIN))
                .body("[1].type", equalTo("ETC"))
                .body("[1].requiresPrescription", equalTo(true))
                .body("[2].medicineId", equalTo(MED_BERBERIN))
                .body("[2].type", equalTo("OTC"))
                .body("[2].requiresPrescription", equalTo(false));
    }

    @Test
    @Order(8)
    @DisplayName("8. GET /for-me: Loại trừ các thuốc người dùng đã từng mua")
    void testForMeExcludesPurchasedMedicines() {
        String userId = UUID.randomUUID().toString();
        String token = generateToken(userId, "KHACH_HANG");

        // User đã mua Panadol và Vitamin C
        userPurchaseHistoryRepository.addPurchasedMedicines(userId, List.of(MED_PANADOL, MED_VITAMIN_C));

        // Panadol hay mua kèm với Vitamin C và Berberin
        cooccurrenceRepository.incrementCooccurrence(MED_PANADOL, MED_VITAMIN_C);
        cooccurrenceRepository.incrementCooccurrence(MED_PANADOL, MED_BERBERIN);

        MedicineResponse resBerberin = new MedicineResponse(
                MED_BERBERIN, "MED-004", "Berberin", "Berberin", "OPC", "Lọ",
                BigDecimal.valueOf(35000), null, null, null, null, "OTC", "cat-3", "Tiêu hóa", true, 5.0, 1, Instant.now(), Instant.now()
        );
        MedicineResponse resVitC = new MedicineResponse(
                MED_VITAMIN_C, "MED-003", "Vitamin C", "Vit C", "DHG", "Hộp",
                BigDecimal.valueOf(45000), null, null, null, null, "OTC", "cat-1", "Vitamin", true, 5.0, 1, Instant.now(), Instant.now()
        );

        when(medicineServiceClient.getMedicinesBatch(anyString())).thenReturn(List.of(resBerberin, resVitC));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/recommendations/for-me?limit=5")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(1))
                // Vitamin C đã mua nên bị loại, chỉ còn Berberin
                .body("[0].medicineId", equalTo(MED_BERBERIN))
                .body("[0].reason", equalTo("FOR_YOU"));
    }

    @Test
    @Order(9)
    @DisplayName("9. GET /for-me: NGUYÊN TẮC AN TOÀN - TUYỆT ĐỐI KHÔNG GỢI Ý THUỐC ETC")
    void testForMeStrictlyExcludesEtcMedicines() {
        String userId = UUID.randomUUID().toString();
        String token = generateToken(userId, "KHACH_HANG");

        // User đã mua Panadol
        userPurchaseHistoryRepository.addPurchasedMedicines(userId, List.of(MED_PANADOL));

        // Panadol hay mua kèm với Augmentin (ETC) và Berberin (OTC)
        for (int i = 0; i < 15; i++) cooccurrenceRepository.incrementCooccurrence(MED_PANADOL, MED_AUGMENTIN);
        for (int i = 0; i < 5; i++) cooccurrenceRepository.incrementCooccurrence(MED_PANADOL, MED_BERBERIN);

        MedicineResponse resAugmentin = new MedicineResponse(
                MED_AUGMENTIN, "MED-005", "Augmentin 1g", "Amox", "GSK", "Hộp",
                BigDecimal.valueOf(285000), null, null, null, null, "ETC", "cat-2", "Kháng sinh", true, 5.0, 1, Instant.now(), Instant.now()
        );
        MedicineResponse resBerberin = new MedicineResponse(
                MED_BERBERIN, "MED-004", "Berberin", "Berberin", "OPC", "Lọ",
                BigDecimal.valueOf(35000), null, null, null, null, "OTC", "cat-3", "Tiêu hóa", true, 5.0, 1, Instant.now(), Instant.now()
        );

        when(medicineServiceClient.getMedicinesBatch(anyString())).thenReturn(List.of(resAugmentin, resBerberin));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/recommendations/for-me?limit=5")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(1))
                // Dù Augmentin có điểm cao hơn (15 > 5), nhưng vì là ETC nên BỊ LOẠI THẲNG TAY
                .body("[0].medicineId", equalTo(MED_BERBERIN))
                .body("[0].type", equalTo("OTC"))
                .body("[0].requiresPrescription", equalTo(false))
                .body("[0].reason", equalTo("FOR_YOU"));
    }

    @Test
    @Order(10)
    @DisplayName("10. GET /for-me: Fallback sang Top bán chạy (BEST_SELLER) cho khách hàng mới chưa từng mua")
    void testForMeFallbackBestSellersForNewUser() {
        String newUserId = UUID.randomUUID().toString();
        String token = generateToken(newUserId, "KHACH_HANG");

        // Tạo dữ liệu top bán chạy trong product_stats:
        // Panadol: 100 lượt mua (OTC)
        // Augmentin: 90 lượt mua (ETC)
        // Berberin: 50 lượt mua (OTC)
        for (int i = 0; i < 100; i++) productStatsRepository.incrementPurchaseCount(MED_PANADOL);
        for (int i = 0; i < 90; i++) productStatsRepository.incrementPurchaseCount(MED_AUGMENTIN);
        for (int i = 0; i < 50; i++) productStatsRepository.incrementPurchaseCount(MED_BERBERIN);

        MedicineResponse resPanadol = new MedicineResponse(
                MED_PANADOL, "MED-002", "Panadol Extra", "Paracetamol", "GSK", "Hộp",
                BigDecimal.valueOf(55000), null, null, null, null, "OTC", "cat-1", "Giảm đau", true, 5.0, 1, Instant.now(), Instant.now()
        );
        MedicineResponse resAugmentin = new MedicineResponse(
                MED_AUGMENTIN, "MED-005", "Augmentin 1g", "Amox", "GSK", "Hộp",
                BigDecimal.valueOf(285000), null, null, null, null, "ETC", "cat-2", "Kháng sinh", true, 5.0, 1, Instant.now(), Instant.now()
        );
        MedicineResponse resBerberin = new MedicineResponse(
                MED_BERBERIN, "MED-004", "Berberin", "Berberin", "OPC", "Lọ",
                BigDecimal.valueOf(35000), null, null, null, null, "OTC", "cat-3", "Tiêu hóa", true, 5.0, 1, Instant.now(), Instant.now()
        );

        when(medicineServiceClient.getMedicinesBatch(anyString())).thenReturn(List.of(resPanadol, resAugmentin, resBerberin));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/recommendations/for-me?limit=5")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(2))
                // Fallback bán chạy chỉ lấy OTC, loại bỏ Augmentin (ETC)
                .body("[0].medicineId", equalTo(MED_PANADOL))
                .body("[0].reason", equalTo("BEST_SELLER"))
                .body("[1].medicineId", equalTo(MED_BERBERIN))
                .body("[1].reason", equalTo("BEST_SELLER"));
    }

    @Test
    @Order(11)
    @DisplayName("11. GET /related: Trả đúng danh sách thuốc cùng danh mục từ medicine-service")
    void testRelatedMedicinesSameCategory() {
        String catId = "cat-pain-relief";
        MedicineResponse originMed = new MedicineResponse(
                MED_PANADOL, "MED-002", "Panadol Extra", "Paracetamol", "GSK", "Hộp",
                BigDecimal.valueOf(55000), null, null, null, null, "OTC", catId, "Giảm đau", true, 5.0, 1, Instant.now(), Instant.now()
        );

        MedicineResponse relatedMed1 = new MedicineResponse(
                MED_BERBERIN, "MED-004", "Efferalgan 500mg", "Paracetamol", "UPSA", "Hộp",
                BigDecimal.valueOf(60000), null, null, null, null, "OTC", catId, "Giảm đau", true, 5.0, 1, Instant.now(), Instant.now()
        );
        MedicineResponse relatedMed2 = new MedicineResponse(
                MED_AUGMENTIN, "MED-005", "Morphine 10mg", "Morphine", "Central", "Ống",
                BigDecimal.valueOf(150000), null, null, null, null, "ETC", catId, "Giảm đau", true, 5.0, 1, Instant.now(), Instant.now()
        );

        when(medicineServiceClient.getMedicineById(MED_PANADOL)).thenReturn(originMed);
        when(medicineServiceClient.getMedicinesByCategory(eq(catId), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(List.of(originMed, relatedMed1, relatedMed2), 0, 10, 3, 1, true));

        given()
                .when()
                .get("/api/v1/recommendations/related/" + MED_PANADOL + "?limit=5")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(2))
                // Loại trừ chính thuốc Panadol, giữ relatedMed1 và relatedMed2
                .body("[0].medicineId", equalTo(MED_BERBERIN))
                .body("[0].reason", equalTo("SAME_CATEGORY"))
                .body("[1].medicineId", equalTo(MED_AUGMENTIN))
                .body("[1].requiresPrescription", equalTo(true))
                .body("[1].reason", equalTo("SAME_CATEGORY"));
    }

    @Test
    @Order(12)
    @DisplayName("12. Khả năng chịu lỗi: medicine-service lỗi/timeout, fallback kích hoạt an toàn, không trả 500")
    void testMedicineServiceFailureFallback() {
        cooccurrenceRepository.incrementCooccurrence(MED_PANADOL, MED_VITAMIN_C);

        // Giả lập medicine-service bị sập hoặc timeout
        when(medicineServiceClient.getMedicinesBatch(anyString())).thenThrow(new RuntimeException("Connection refused: medicine-service is down"));
        when(medicineServiceClient.getMedicineById(anyString())).thenThrow(new RuntimeException("Timeout"));

        // 1. also-bought trả fallback an toàn
        given()
                .when()
                .get("/api/v1/recommendations/also-bought/" + MED_PANADOL + "?limit=5")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(1))
                .body("[0].medicineId", equalTo(MED_VITAMIN_C))
                .body("[0].reason", equalTo("FREQUENTLY_BOUGHT_TOGETHER"));

        // 2. related trả rỗng an toàn
        given()
                .when()
                .get("/api/v1/recommendations/related/" + MED_PANADOL + "?limit=5")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(0));
    }

    @Test
    @Order(13)
    @DisplayName("13. Validation & Phân quyền: Limit sai trả 400 VALIDATION_ERROR; gọi /for-me không token trả 401")
    void testValidationAndSecurity() {
        // Limit < 1
        given()
                .when()
                .get("/api/v1/recommendations/also-bought/" + MED_PANADOL + "?limit=0")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("code", equalTo("VALIDATION_ERROR"));

        // Limit > 20
        given()
                .when()
                .get("/api/v1/recommendations/also-bought/" + MED_PANADOL + "?limit=25")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("code", equalTo("VALIDATION_ERROR"));

        // /for-me không có JWT Bearer token
        given()
                .when()
                .get("/api/v1/recommendations/for-me")
                .then()
                .statusCode(401);
    }
}
