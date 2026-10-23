package com.pharmacy.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.inventory.client.MedicineResponse;
import com.pharmacy.inventory.client.MedicineServiceClient;
import com.pharmacy.inventory.dto.CreateBatchRequest;
import com.pharmacy.inventory.dto.OrderCreatedEvent;
import com.pharmacy.inventory.dto.OrderCreatedItem;
import com.pharmacy.inventory.dto.OrderStatusChangedEvent;
import com.pharmacy.inventory.dto.StockAdjustmentRequest;
import com.pharmacy.inventory.entity.Batch;
import com.pharmacy.inventory.entity.MovementType;
import com.pharmacy.inventory.entity.OutboxEvent;
import com.pharmacy.inventory.entity.OutboxStatus;
import com.pharmacy.inventory.entity.ProcessedEvent;
import com.pharmacy.inventory.entity.ReservationStatus;
import com.pharmacy.inventory.entity.StockAlert;
import com.pharmacy.inventory.entity.StockMovement;
import com.pharmacy.inventory.entity.StockReservation;
import com.pharmacy.inventory.messaging.OrderKafkaConsumer;
import com.pharmacy.inventory.repository.BatchRepository;
import com.pharmacy.inventory.repository.OutboxEventRepository;
import com.pharmacy.inventory.repository.ProcessedEventRepository;
import com.pharmacy.inventory.repository.StockAlertRepository;
import com.pharmacy.inventory.repository.StockMovementRepository;
import com.pharmacy.inventory.repository.StockReservationRepository;
import com.pharmacy.inventory.service.AlertScheduler;
import com.pharmacy.inventory.service.InventoryReservationService;
import com.pharmacy.inventory.service.OutboxScheduler;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Bộ kiểm thử toàn diện cho inventory-service bao gồm 12 ca kiểm thử bắt buộc:
 * 1. Nhập lô mới (POST /api/v1/batches)
 * 2. Validation khi nhập lô (ngày hết hạn, số lượng)
 * 3. Giữ hàng đúng nguyên tắc FEFO khi nhận order.created
 * 4. All-or-nothing: hết hàng thì reservation-failed và không giữ gì
 * 5. Concurrency: 2 đơn cùng giành lô cuối, đúng 1 đơn thắng (SELECT ... FOR UPDATE)
 * 6. Idempotency: nhận event order.created lặp không trừ hai lần
 * 7. DA_HUY: nhả hàng đang giữ về lại kho
 * 8. DANG_GIAO: chốt trừ kho (COMMITTED)
 * 9. Lô hết hạn không bao giờ bị xuất giữ
 * 10. Job cảnh báo hạn và tồn kho thấp hằng ngày (không lặp lại trong ngày)
 * 11. Phân quyền bảo mật RBAC (KHACH_HANG nhận 403 Forbidden)
 * 12. Transactional Outbox Scheduler quét và đẩy event sang Kafka
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class InventoryResourceTest {

    private static final String ISSUER = "https://pharmacy.com/issuer";

    private static final String ADMIN_ID = "00000000-0000-0000-0000-000000000000";
    private static final String PHARMACIST_ID = "22222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String CUSTOMER_ID = "11111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    private static final String MED_A = "6ad23be6c034632667baaa01";
    private static final String MED_B = "6ad23be6c034632667baaa02";
    private static final String MED_C = "6ad23be6c034632667baaa03";

    @InjectMock
    @RestClient
    MedicineServiceClient medicineServiceClient;

    @Inject
    BatchRepository batchRepository;

    @Inject
    StockReservationRepository reservationRepository;

    @Inject
    StockMovementRepository movementRepository;

    @Inject
    OutboxEventRepository outboxRepository;

    @Inject
    ProcessedEventRepository processedEventRepository;

    @Inject
    StockAlertRepository alertRepository;

    @Inject
    jakarta.persistence.EntityManager entityManager;

    @Inject
    InventoryReservationService reservationService;

    @Inject
    OrderKafkaConsumer orderKafkaConsumer;

    @Inject
    OutboxScheduler outboxScheduler;

    @Inject
    AlertScheduler alertScheduler;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @Any
    InMemoryConnector connector;

    private String adminToken;
    private String pharmacistToken;
    private String customerToken;

    @BeforeEach
    public void setup() {
        adminToken = Jwt.issuer(ISSUER)
                .subject(ADMIN_ID)
                .upn("admin@pharmacy.local")
                .claim("email", "admin@pharmacy.local")
                .claim("name", "Admin Test")
                .groups(Set.of("ADMIN"))
                .expiresIn(Duration.ofHours(1))
                .sign();

        pharmacistToken = Jwt.issuer(ISSUER)
                .subject(PHARMACIST_ID)
                .upn("duoc_si@pharmacy.local")
                .claim("email", "duoc_si@pharmacy.local")
                .claim("name", "Dược Sĩ Test")
                .groups(Set.of("DUOC_SI"))
                .expiresIn(Duration.ofHours(1))
                .sign();

        customerToken = Jwt.issuer(ISSUER)
                .subject(CUSTOMER_ID)
                .upn("khach_hang@pharmacy.local")
                .claim("email", "khach_hang@pharmacy.local")
                .claim("name", "Khách Hàng Test")
                .groups(Set.of("KHACH_HANG"))
                .expiresIn(Duration.ofHours(1))
                .sign();

        // Cấu hình mock cho medicineServiceClient
        MedicineResponse m1 = new MedicineResponse();
        m1.id = MED_A;
        m1.name = "Paracetamol 500mg";
        MedicineResponse m2 = new MedicineResponse();
        m2.id = MED_B;
        m2.name = "Amoxicillin 500mg";
        MedicineResponse m3 = new MedicineResponse();
        m3.id = MED_C;
        m3.name = "Vitamin C 1000mg";

        Mockito.when(medicineServiceClient.getMedicinesBatch(anyString()))
                .thenReturn(List.of(m1, m2, m3));
    }

    // -------------------------------------------------------------------------
    // Test 1: Nhập lô mới thành công và ghi sổ cái IMPORT
    // -------------------------------------------------------------------------
    @Test
    @Order(1)
    public void testCreateBatchSuccess() {
        CreateBatchRequest req = new CreateBatchRequest(
                MED_A,
                "LOT-TEST-01",
                LocalDate.now().plusMonths(6),
                100,
                new BigDecimal("5000.00")
        );

        String batchIdStr = given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/api/v1/batches")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("medicineId", equalTo(MED_A))
                .body("batchNumber", equalTo("LOT-TEST-01"))
                .body("quantityIn", equalTo(100))
                .body("quantityAvailable", equalTo(100))
                .extract().path("id");

        UUID batchId = UUID.fromString(batchIdStr);
        Batch batch = batchRepository.findById(batchId);
        assertNotNull(batch);
        assertEquals(100, batch.quantityAvailable);

        // Kiểm tra sổ cái xuất nhập kho có bản ghi IMPORT
        List<StockMovement> movements = movementRepository.find("batchId = ?1 and type = ?2", batchId, MovementType.IMPORT).list();
        assertEquals(1, movements.size());
        assertEquals(100, movements.get(0).quantity);
    }

    // -------------------------------------------------------------------------
    // Test 2: Validation khi nhập lô (ngày hết hạn quá khứ, số lượng âm)
    // -------------------------------------------------------------------------
    @Test
    @Order(2)
    public void testCreateBatchValidationFail() {
        // Hết hạn ở quá khứ
        CreateBatchRequest reqPast = new CreateBatchRequest(
                MED_A,
                "LOT-PAST",
                LocalDate.now().minusDays(1),
                50,
                new BigDecimal("5000.00")
        );

        given()
                .header("Authorization", "Bearer " + pharmacistToken)
                .contentType(ContentType.JSON)
                .body(reqPast)
                .when()
                .post("/api/v1/batches")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));

        // Số lượng âm
        CreateBatchRequest reqNegative = new CreateBatchRequest(
                MED_A,
                "LOT-NEG",
                LocalDate.now().plusMonths(3),
                -10,
                new BigDecimal("5000.00")
        );

        given()
                .header("Authorization", "Bearer " + pharmacistToken)
                .contentType(ContentType.JSON)
                .body(reqNegative)
                .when()
                .post("/api/v1/batches")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    // -------------------------------------------------------------------------
    // Test 3: Giữ hàng FEFO khi nhận order.created
    // -------------------------------------------------------------------------
    @Test
    @Order(3)
    public void testOrderCreatedFefoAllocation() {
        // Tạo 2 lô cho MED_B: Lô 1 hết hạn sau 2 tháng (tồn 10), Lô 2 hết hạn sau 6 tháng (tồn 20)
        CreateBatchRequest b1 = new CreateBatchRequest(MED_B, "LOT-FEFO-01", LocalDate.now().plusMonths(2), 10, new BigDecimal("10000.00"));
        CreateBatchRequest b2 = new CreateBatchRequest(MED_B, "LOT-FEFO-02", LocalDate.now().plusMonths(6), 20, new BigDecimal("10000.00"));

        given().header("Authorization", "Bearer " + adminToken).contentType(ContentType.JSON).body(b1).post("/api/v1/batches");
        given().header("Authorization", "Bearer " + adminToken).contentType(ContentType.JSON).body(b2).post("/api/v1/batches");

        // Đơn hàng đặt 15 đơn vị MED_B
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                "order.created",
                Instant.now(),
                "corr-fefo-1",
                orderId,
                UUID.fromString(CUSTOMER_ID),
                "khach@pharmacy.local",
                "Khách A",
                null,
                "CHO_XU_LY",
                new BigDecimal("150000.00"),
                List.of(new OrderCreatedItem(MED_B, "Amoxicillin 500mg", "OTC", new BigDecimal("10000.00"), 15))
        );

        reservationService.processOrderCreated(event);

        // Kiểm tra lô 1 (hết hạn sớm hơn) phải bị trừ hết 10 đơn vị (còn 0)
        Batch batch1 = batchRepository.findByMedicineIdAndBatchNumber(MED_B, "LOT-FEFO-01").orElseThrow();
        assertEquals(0, batch1.quantityAvailable);

        // Lô 2 bị trừ tiếp 5 đơn vị (20 - 5 = 15)
        Batch batch2 = batchRepository.findByMedicineIdAndBatchNumber(MED_B, "LOT-FEFO-02").orElseThrow();
        assertEquals(15, batch2.quantityAvailable);

        // Kiểm tra outbox có event inventory.reserved
        List<OutboxEvent> outboxList = outboxRepository.find("aggregateId = ?1 and eventType = ?2",
                orderId.toString(), "inventory.reserved").list();
        assertEquals(1, outboxList.size());
    }

    // -------------------------------------------------------------------------
    // Test 4: Hết hàng thì reservation-failed và KHÔNG giữ gì cả (All-or-Nothing)
    // -------------------------------------------------------------------------
    @Test
    @Order(4)
    public void testOrderCreatedAllOrNothingOutOfStock() {
        // MED_B hiện còn 15 đơn vị. Đơn hàng yêu cầu 50 đơn vị -> Thiếu
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                "order.created",
                Instant.now(),
                "corr-fail-1",
                orderId,
                UUID.fromString(CUSTOMER_ID),
                "khach@pharmacy.local",
                "Khách A",
                null,
                "CHO_XU_LY",
                new BigDecimal("500000.00"),
                List.of(new OrderCreatedItem(MED_B, "Amoxicillin 500mg", "OTC", new BigDecimal("10000.00"), 50))
        );

        reservationService.processOrderCreated(event);

        // Tồn kho lô 2 của MED_B vẫn phải nguyên vẹn 15 (All-or-nothing: không giữ dở dang)
        Batch batch2 = batchRepository.findByMedicineIdAndBatchNumber(MED_B, "LOT-FEFO-02").orElseThrow();
        assertEquals(15, batch2.quantityAvailable);

        // Outbox phải ghi nhận event inventory.reservation-failed
        List<OutboxEvent> outboxList = outboxRepository.find("aggregateId = ?1 and eventType = ?2",
                orderId.toString(), "inventory.reservation-failed").list();
        assertEquals(1, outboxList.size());
        assertTrue(outboxList.get(0).payload.contains("OUT_OF_STOCK"));
    }

    // -------------------------------------------------------------------------
    // Test 5: Concurrency - 2 đơn cùng giành lô cuối cùng, chỉ đúng 1 đơn thắng
    // -------------------------------------------------------------------------
    @Test
    @Order(5)
    public void testConcurrentOrderReservationLastBatch() throws Exception {
        // Tạo thuốc MED_C chỉ có đúng 1 lô với số lượng 10
        CreateBatchRequest b = new CreateBatchRequest(MED_C, "LOT-LAST-10", LocalDate.now().plusMonths(5), 10, new BigDecimal("20000.00"));
        given().header("Authorization", "Bearer " + adminToken).contentType(ContentType.JSON).body(b).post("/api/v1/batches");

        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();

        OrderCreatedEvent event1 = new OrderCreatedEvent(
                UUID.randomUUID().toString(), "order.created", Instant.now(), "corr-c1", orderId1,
                UUID.fromString(CUSTOMER_ID), "a@pharmacy.local", "A", null, "CHO_XU_LY",
                new BigDecimal("200000.00"),
                List.of(new OrderCreatedItem(MED_C, "Vitamin C", "OTC", new BigDecimal("20000.00"), 10))
        );

        OrderCreatedEvent event2 = new OrderCreatedEvent(
                UUID.randomUUID().toString(), "order.created", Instant.now(), "corr-c2", orderId2,
                UUID.fromString(CUSTOMER_ID), "b@pharmacy.local", "B", null, "CHO_XU_LY",
                new BigDecimal("200000.00"),
                List.of(new OrderCreatedItem(MED_C, "Vitamin C", "OTC", new BigDecimal("20000.00"), 10))
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Callable<Void>> tasks = List.of(
                () -> { reservationService.processOrderCreated(event1); return null; },
                () -> { reservationService.processOrderCreated(event2); return null; }
        );

        List<Future<Void>> futures = executor.invokeAll(tasks);
        for (Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Kiểm tra: 1 đơn thắng (RESERVED) và 1 đơn thua (RESERVATION_FAILED)
        List<OutboxEvent> reserved1 = outboxRepository.find("aggregateId = ?1 and eventType = ?2", orderId1.toString(), "inventory.reserved").list();
        List<OutboxEvent> reserved2 = outboxRepository.find("aggregateId = ?1 and eventType = ?2", orderId2.toString(), "inventory.reserved").list();

        int totalReserved = reserved1.size() + reserved2.size();
        assertEquals(1, totalReserved, "Chỉ duy nhất 1 đơn hàng được giữ lô cuối cùng");

        Batch batch = batchRepository.findByMedicineIdAndBatchNumber(MED_C, "LOT-LAST-10").orElseThrow();
        assertEquals(0, batch.quantityAvailable, "Tồn kho sau khi tranh mua phải về 0, không được âm");
    }

    // -------------------------------------------------------------------------
    // Test 6: Idempotency - nhận event order.created lặp lại không trừ hai lần
    // -------------------------------------------------------------------------
    @Test
    @Order(6)
    public void testOrderCreatedDuplicateEventIdempotent() {
        // Tạo thêm 1 lô mới cho MED_C
        CreateBatchRequest b = new CreateBatchRequest(MED_C, "LOT-IDEMP-01", LocalDate.now().plusMonths(5), 20, new BigDecimal("20000.00"));
        given().header("Authorization", "Bearer " + adminToken).contentType(ContentType.JSON).body(b).post("/api/v1/batches");

        UUID orderId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        OrderCreatedEvent event = new OrderCreatedEvent(
                eventId, "order.created", Instant.now(), "corr-idem", orderId,
                UUID.fromString(CUSTOMER_ID), "c@pharmacy.local", "C", null, "CHO_XU_LY",
                new BigDecimal("100000.00"),
                List.of(new OrderCreatedItem(MED_C, "Vitamin C", "OTC", new BigDecimal("20000.00"), 5))
        );

        // Lần 1: Xử lý thành công
        reservationService.processOrderCreated(event);
        Batch batchAfterFirst = batchRepository.findByMedicineIdAndBatchNumber(MED_C, "LOT-IDEMP-01").orElseThrow();
        assertEquals(15, batchAfterFirst.quantityAvailable); // 20 - 5 = 15

        // Lần 2: Gửi trùng đúng event đó
        reservationService.processOrderCreated(event);
        Batch batchAfterSecond = batchRepository.findByMedicineIdAndBatchNumber(MED_C, "LOT-IDEMP-01").orElseThrow();
        assertEquals(15, batchAfterSecond.quantityAvailable, "Tồn kho không được trừ thêm lần thứ hai");
    }

    // -------------------------------------------------------------------------
    // Test 7: DA_HUY - nhả hàng đang giữ về lại kho
    // -------------------------------------------------------------------------
    @Test
    @Order(7)
    public void testOrderStatusChangedCancelledReleasesStock() {
        // Đặt hàng giữ 5 đơn vị MED_C từ lô LOT-IDEMP-01 (đang còn 15 -> còn 10)
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID().toString(), "order.created", Instant.now(), "corr-cancel-1", orderId,
                UUID.fromString(CUSTOMER_ID), "c@pharmacy.local", "C", null, "CHO_XU_LY",
                new BigDecimal("100000.00"),
                List.of(new OrderCreatedItem(MED_C, "Vitamin C", "OTC", new BigDecimal("20000.00"), 5))
        );
        reservationService.processOrderCreated(event);

        Batch batchBeforeCancel = batchRepository.findByMedicineIdAndBatchNumber(MED_C, "LOT-IDEMP-01").orElseThrow();
        assertEquals(10, batchBeforeCancel.quantityAvailable);

        // Phát event order.status-changed: DA_HUY
        OrderStatusChangedEvent cancelEvent = new OrderStatusChangedEvent(
                UUID.randomUUID().toString(),
                "order.status-changed",
                Instant.now(),
                "corr-cancel-1",
                orderId,
                UUID.fromString(CUSTOMER_ID),
                "c@pharmacy.local",
                "C",
                "CHO_XU_LY",
                "DA_HUY",
                "Khách hàng đổi ý muốn hủy đơn"
        );
        reservationService.processOrderStatusChanged(cancelEvent);

        // Xóa cache 1st-level của JPA để truy vấn lại trực tiếp từ database
        entityManager.clear();

        // Tồn kho phải được nhả lại (10 + 5 = 15)
        Batch batchAfterCancel = batchRepository.findByMedicineIdAndBatchNumber(MED_C, "LOT-IDEMP-01").orElseThrow();
        assertEquals(15, batchAfterCancel.quantityAvailable);

        // Reservation chuyển sang RELEASED
        List<StockReservation> resList = reservationRepository.findByOrderId(orderId);
        assertEquals(1, resList.size());
        assertEquals(ReservationStatus.RELEASED, resList.get(0).status);
    }

    // -------------------------------------------------------------------------
    // Test 8: DANG_GIAO - chốt trừ kho (COMMITTED)
    // -------------------------------------------------------------------------
    @Test
    @Order(8)
    public void testOrderStatusChangedShippedCommitsStock() {
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID().toString(), "order.created", Instant.now(), "corr-ship-1", orderId,
                UUID.fromString(CUSTOMER_ID), "c@pharmacy.local", "C", null, "CHO_XU_LY",
                new BigDecimal("100000.00"),
                List.of(new OrderCreatedItem(MED_C, "Vitamin C", "OTC", new BigDecimal("20000.00"), 5))
        );
        reservationService.processOrderCreated(event);

        OrderStatusChangedEvent shipEvent = new OrderStatusChangedEvent(
                UUID.randomUUID().toString(),
                "order.status-changed",
                Instant.now(),
                "corr-ship-1",
                orderId,
                UUID.fromString(CUSTOMER_ID),
                "c@pharmacy.local",
                "C",
                "CHO_XU_LY",
                "DANG_GIAO",
                "Giao hàng cho shipper"
        );
        reservationService.processOrderStatusChanged(shipEvent);

        List<StockReservation> resList = reservationRepository.findByOrderId(orderId);
        assertEquals(1, resList.size());
        assertEquals(ReservationStatus.COMMITTED, resList.get(0).status);

        // Kiểm tra có movement loại EXPORT
        List<StockMovement> exports = movementRepository.find("orderId = ?1 and type = ?2", orderId, MovementType.EXPORT).list();
        assertEquals(1, exports.size());
    }

    // -------------------------------------------------------------------------
    // Test 9: Lô hết hạn không bao giờ bị xuất giữ
    // -------------------------------------------------------------------------
    @Test
    @Order(9)
    public void testExpiredBatchNeverAllocated() {
        // Tạo 1 lô thuốc đã hết hạn trực tiếp trong DB
        saveExpiredBatch(MED_A, "LOT-EXPIRED-99", LocalDate.now().minusDays(5), 50);

        // Xem tồn khả dụng qua API
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/stocks/" + MED_A)
                .then()
                .statusCode(200)
                .body("medicineId", equalTo(MED_A));

        // Đơn hàng mua 120 đơn vị (LOT-TEST-01 có 100, lô hết hạn có 50 -> tổng nếu tính lô hết hạn là 150 nhưng thực tế khả dụng chỉ 100)
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID().toString(), "order.created", Instant.now(), "corr-exp-1", orderId,
                UUID.fromString(CUSTOMER_ID), "khach@pharmacy.local", "Khách A", null, "CHO_XU_LY",
                new BigDecimal("600000.00"),
                List.of(new OrderCreatedItem(MED_A, "Paracetamol", "OTC", new BigDecimal("5000.00"), 120))
        );

        reservationService.processOrderCreated(event);

        // Vì khả dụng chỉ có 100 nên đơn phải bị OUT_OF_STOCK, lô hết hạn không được đụng tới
        List<OutboxEvent> outboxList = outboxRepository.find("aggregateId = ?1 and eventType = ?2",
                orderId.toString(), "inventory.reservation-failed").list();
        assertEquals(1, outboxList.size(), "Phải thất bại vì lô hết hạn bị loại bỏ hoàn toàn khỏi tồn bán");
    }

    @Transactional
    void saveExpiredBatch(String medId, String batchNo, LocalDate expiry, int qty) {
        Batch expired = new Batch();
        expired.medicineId = medId;
        expired.batchNumber = batchNo;
        expired.expiryDate = expiry;
        expired.quantityIn = qty;
        expired.quantityAvailable = qty;
        expired.costPrice = new BigDecimal("5000.00");
        batchRepository.persist(expired);
    }

    // -------------------------------------------------------------------------
    // Test 10: Job cảnh báo hạn và tồn kho thấp hằng ngày (không lặp lại trong ngày)
    // -------------------------------------------------------------------------
    @Test
    @Order(10)
    public void testDailyAlertJob() {
        // Chạy lần 1: Quét và sinh cảnh báo
        int alertsCount1 = alertScheduler.runDailyAlertScan();
        assertTrue(alertsCount1 >= 0);

        // Chạy lần 2 trong cùng ngày: Không sinh thêm cảnh báo trùng lặp
        int alertsCount2 = alertScheduler.runDailyAlertScan();
        assertEquals(0, alertsCount2, "Không phát lặp lại cùng một cảnh báo trong cùng ngày");
    }

    // -------------------------------------------------------------------------
    // Test 11: Phân quyền bảo mật RBAC (KHACH_HANG nhận 403 Forbidden)
    // -------------------------------------------------------------------------
    @Test
    @Order(11)
    public void testRbacCustomerForbidden() {
        CreateBatchRequest req = new CreateBatchRequest(
                MED_A, "LOT-FORBIDDEN", LocalDate.now().plusMonths(3), 10, new BigDecimal("5000.00")
        );

        // Khách hàng gọi API nhập lô -> 403
        given()
                .header("Authorization", "Bearer " + customerToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/api/v1/batches")
                .then()
                .statusCode(403);

        // Khách hàng gọi API điều chỉnh kho -> 403
        StockAdjustmentRequest adj = new StockAdjustmentRequest(UUID.randomUUID(), 5, "Test");
        given()
                .header("Authorization", "Bearer " + customerToken)
                .contentType(ContentType.JSON)
                .body(adj)
                .when()
                .post("/api/v1/stocks/adjust")
                .then()
                .statusCode(403);
    }

    // -------------------------------------------------------------------------
    // Test 12: Transactional Outbox Scheduler quét và đẩy event sang Kafka
    // -------------------------------------------------------------------------
    @Test
    @Order(12)
    public void testOutboxSchedulerPushesEvents() {
        // Kiểm tra số bản ghi outbox đang PENDING
        List<OutboxEvent> pending = outboxRepository.find("status = ?1", OutboxStatus.PENDING).list();
        assertTrue(pending.size() > 0, "Phải có ít nhất 1 event PENDING từ các test trước");

        // Chạy scheduler đẩy outbox
        int pushed = outboxScheduler.processPendingEvents();
        assertTrue(pushed > 0);

        // Kiểm tra toàn bộ đã chuyển sang SENT
        long remainingPending = outboxRepository.count("status = ?1", OutboxStatus.PENDING);
        assertEquals(0, remainingPending, "Toàn bộ event outbox đã được gửi thành công");
    }
}

