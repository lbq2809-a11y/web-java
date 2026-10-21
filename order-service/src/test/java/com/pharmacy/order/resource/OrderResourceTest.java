package com.pharmacy.order.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.order.client.MedicineServiceClient;
import com.pharmacy.order.client.PrescriptionServiceClient;
import com.pharmacy.order.dto.AddToCartRequest;
import com.pharmacy.order.dto.CancelOrderRequest;
import com.pharmacy.order.dto.CheckoutRequest;
import com.pharmacy.order.dto.LinkOrderResponse;
import com.pharmacy.order.dto.MedicineResponse;
import com.pharmacy.order.dto.PaymentCompletedEvent;
import com.pharmacy.order.dto.PaymentFailedEvent;
import com.pharmacy.order.dto.RefundOrderRequest;
import com.pharmacy.order.dto.ShippingAddressRequest;
import com.pharmacy.order.dto.UpdateCartItemRequest;
import com.pharmacy.order.dto.UpdateOrderStatusRequest;
import com.pharmacy.order.dto.ValidatePrescriptionResponse;
import com.pharmacy.order.entity.OrderStatus;
import com.pharmacy.order.repository.ProcessedEventRepository;
import com.pharmacy.order.service.CartRedisService;
import com.pharmacy.order.service.PaymentKafkaConsumer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Bộ kiểm thử tích hợp (Integration Test) cho order-service (Giai đoạn 1).
 * Bao phủ đầy đủ 9 ca kiểm thử: Thao tác giỏ hàng Redis, Checkout giỏ rỗng,
 * Đặt hàng thuốc OTC, Ràng buộc thuốc kê đơn ETC, Thẩm định đơn thuốc,
 * Tính Idempotent chống đặt trùng, và Bảo mật phân quyền.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OrderResourceTest {

    private static final String ISSUER = "https://pharmacy.com/issuer";

    private static final String CUSTOMER_A_ID = "11111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String CUSTOMER_B_ID = "22222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String PHARMACIST_ID = "33333333-cccc-cccc-cccc-cccccccccccc";
    private static final String ADMIN_ID      = "44444444-dddd-dddd-dddd-dddddddddddd";

    private static final String OTC_MED_ID = "6ad23be6c034632667baaa01";
    private static final String ETC_MED_ID = "6ad23be6c034632667baaa05";

    private static final UUID VALID_PRESCRIPTION_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID INVALID_PRESCRIPTION_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    private static String otcOrderId;
    private static String etcOrderId;

    @InjectMock
    @RestClient
    MedicineServiceClient medicineClient;

    @InjectMock
    @RestClient
    PrescriptionServiceClient prescriptionClient;

    @Inject
    PaymentKafkaConsumer paymentConsumer;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ProcessedEventRepository processedEventRepo;

    @Inject
    CartRedisService cartRedisService;

    @Inject
    EntityManager em;

    private static boolean initialized = false;

    private String generateToken(String userId, String role, String fullName) {
        return Jwt.issuer(ISSUER)
                .subject(userId)
                .upn(role.toLowerCase() + "@pharmacy.local")
                .claim("email", role.toLowerCase() + "@pharmacy.local")
                .claim("name", fullName)
                .groups(Set.of(role))
                .expiresIn(Duration.ofHours(1))
                .sign();
    }

    @BeforeEach
    @Transactional
    void setUpMock() {
        if (!initialized) {
            em.createNativeQuery("DELETE FROM order_status_history").executeUpdate();
            em.createNativeQuery("DELETE FROM order_items").executeUpdate();
            em.createNativeQuery("DELETE FROM shipping_addresses").executeUpdate();
            em.createNativeQuery("DELETE FROM orders").executeUpdate();
            em.createNativeQuery("DELETE FROM processed_events").executeUpdate();
            cartRedisService.clearCart(UUID.fromString(CUSTOMER_A_ID));
            cartRedisService.clearCart(UUID.fromString(CUSTOMER_B_ID));
            initialized = true;
        }
        MedicineResponse panadol = new MedicineResponse(
                OTC_MED_ID,
                "MED-OTC-001",
                "Panadol Extra",
                "Paracetamol 500mg, Caffeine 65mg",
                "GSK",
                "Hộp 15 vỉ x 12 viên",
                BigDecimal.valueOf(50000.00),
                "https://placehold.co/400x400?text=Panadol",
                "Giảm đau, hạ sốt",
                "Dị ứng Paracetamol",
                "Uống 1-2 viên/lần",
                "OTC",
                "6ad23be6c034632667baaa00",
                "Thuốc giảm đau",
                true,
                5.0,
                10,
                Instant.now(),
                Instant.now()
        );

        MedicineResponse amoxicillin = new MedicineResponse(
                ETC_MED_ID,
                "MED-ETC-003",
                "Amoxicillin 500mg",
                "Amoxicillin 500mg",
                "Domesco",
                "Hộp 10 vỉ x 10 viên",
                BigDecimal.valueOf(120000.00),
                "https://placehold.co/400x400?text=Amoxicillin",
                "Kháng sinh điều trị nhiễm khuẩn",
                "Dị ứng penicillin",
                "Uống theo đơn",
                "ETC",
                "6ad23be6c034632667baaa00",
                "Kháng sinh",
                true,
                5.0,
                5,
                Instant.now(),
                Instant.now()
        );

        Mockito.when(medicineClient.getMedicinesBatch(anyString())).thenAnswer(invocation -> {
            String ids = invocation.getArgument(0);
            List<MedicineResponse> res = new ArrayList<>();
            if (ids != null && ids.contains(OTC_MED_ID)) {
                res.add(panadol);
            }
            if (ids != null && ids.contains(ETC_MED_ID)) {
                res.add(amoxicillin);
            }
            return res;
        });

        // Mock Prescription Service - Đơn hợp lệ
        Mockito.when(prescriptionClient.validateForOrder(eq(VALID_PRESCRIPTION_ID), any()))
                .thenReturn(new ValidatePrescriptionResponse(
                        VALID_PRESCRIPTION_ID,
                        UUID.fromString(CUSTOMER_A_ID),
                        true,
                        "DA_DUYET",
                        Instant.now().plus(Duration.ofDays(30)),
                        List.of()
                ));

        Mockito.when(prescriptionClient.linkOrder(any(), any()))
                .thenReturn(new LinkOrderResponse(
                        UUID.randomUUID(),
                        VALID_PRESCRIPTION_ID,
                        UUID.randomUUID(),
                        Instant.now()
                ));

        // Mock Prescription Service - Đơn hết hạn
        Mockito.when(prescriptionClient.validateForOrder(eq(INVALID_PRESCRIPTION_ID), any()))
                .thenReturn(new ValidatePrescriptionResponse(
                        INVALID_PRESCRIPTION_ID,
                        UUID.fromString(CUSTOMER_A_ID),
                        false,
                        "DA_DUYET",
                        Instant.now().minus(Duration.ofDays(1)),
                        List.of("Đơn thuốc đã hết hiệu lực")
                ));
    }

    // =========================================================================================
    // Ca 1: Khách hàng thêm thuốc vào giỏ và xem chi tiết giỏ hàng (giá thời gian thực)
    // =========================================================================================
    @Test
    @Order(1)
    void testCase01_AddToCartAndGetCartDetail() {
        String token = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");

        // Xóa giỏ hàng cũ trước khi test
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/cart")
                .then()
                .statusCode(204);

        // Thêm 2 hộp Panadol vào giỏ
        AddToCartRequest addReq = new AddToCartRequest(OTC_MED_ID, 2);
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(addReq)
                .when()
                .post("/api/v1/cart/items")
                .then()
                .statusCode(200)
                .body("totalItems", equalTo(2))
                .body("totalAmount", equalTo(100000.0f))
                .body("items[0].medicineId", equalTo(OTC_MED_ID))
                .body("items[0].price", equalTo(50000.0f))
                .body("items[0].subtotal", equalTo(100000.0f));

        // Xem chi tiết giỏ hàng
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cart")
                .then()
                .statusCode(200)
                .body("totalItems", equalTo(2))
                .body("totalAmount", equalTo(100000.0f));
    }

    // =========================================================================================
    // Ca 2: Khách hàng cập nhật số lượng và xóa sản phẩm khỏi giỏ hàng
    // =========================================================================================
    @Test
    @Order(2)
    void testCase02_UpdateQuantityAndRemoveItemFromCart() {
        String token = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");

        // Cập nhật số lượng Panadol từ 2 thành 3
        UpdateCartItemRequest updateReq = new UpdateCartItemRequest(3);
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(updateReq)
                .when()
                .put("/api/v1/cart/items/" + OTC_MED_ID)
                .then()
                .statusCode(200)
                .body("totalItems", equalTo(3))
                .body("totalAmount", equalTo(150000.0f));

        // Xóa Panadol khỏi giỏ hàng
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/cart/items/" + OTC_MED_ID)
                .then()
                .statusCode(204);

        // Giỏ hàng bây giờ phải rỗng
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cart")
                .then()
                .statusCode(200)
                .body("totalItems", equalTo(0))
                .body("items", empty());
    }

    // =========================================================================================
    // Ca 3: Checkout khi giỏ hàng rỗng -> Bị từ chối 400 Bad Request (CART_EMPTY)
    // =========================================================================================
    @Test
    @Order(3)
    void testCase03_CheckoutEmptyCart_FailsWith400() {
        String token = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");

        CheckoutRequest checkoutReq = new CheckoutRequest(
                null,
                new ShippingAddressRequest("Nguyễn Văn A", "0901234567", "123 Nguyễn Văn Cừ, Q5"),
                "Giao giờ hành chính"
        );

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(checkoutReq)
                .when()
                .post("/api/v1/orders/checkout")
                .then()
                .statusCode(400)
                .body("code", equalTo("CART_EMPTY"));
    }

    // =========================================================================================
    // Ca 4: Checkout thành công đơn hàng chỉ gồm thuốc OTC -> 201 Created, giỏ Redis được xóa sạch
    // =========================================================================================
    @Test
    @Order(4)
    void testCase04_CheckoutOTCSuccess_ClearsCart() {
        String token = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");

        // Thêm 2 hộp Panadol vào giỏ (50,000 * 2 = 100,000)
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(new AddToCartRequest(OTC_MED_ID, 2))
                .when()
                .post("/api/v1/cart/items")
                .then()
                .statusCode(200);

        CheckoutRequest checkoutReq = new CheckoutRequest(
                null,
                new ShippingAddressRequest("Nguyễn Văn A", "0901234567", "123 Nguyễn Văn Cừ, Q5"),
                "Giao buổi sáng"
        );

        otcOrderId = given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "idemp-otc-order-001")
                .contentType(ContentType.JSON)
                .body(checkoutReq)
                .when()
                .post("/api/v1/orders/checkout")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("customerId", equalTo(CUSTOMER_A_ID))
                .body("status", equalTo("CHO_XU_LY"))
                .body("totalAmount", equalTo(100000.0f))
                .body("items[0].medicineId", equalTo(OTC_MED_ID))
                .body("items[0].medicineName", equalTo("Panadol Extra"))
                .body("items[0].prescriptionType", equalTo("OTC"))
                .body("shippingAddress.recipientName", equalTo("Nguyễn Văn A"))
                .extract().path("id");

        // Kiểm tra giỏ hàng trên Redis đã được xóa sạch
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cart")
                .then()
                .statusCode(200)
                .body("totalItems", equalTo(0));
    }

    // =========================================================================================
    // Ca 5: Checkout đơn hàng có thuốc kê đơn ETC nhưng KHÔNG truyền prescriptionId -> 400 Bad Request
    // =========================================================================================
    @Test
    @Order(5)
    void testCase05_CheckoutETCWithoutPrescriptionId_FailsWith400() {
        String token = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");

        // Thêm thuốc kháng sinh Amoxicillin (ETC) vào giỏ
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(new AddToCartRequest(ETC_MED_ID, 1))
                .when()
                .post("/api/v1/cart/items")
                .then()
                .statusCode(200);

        CheckoutRequest checkoutReq = new CheckoutRequest(
                null, // Thiếu prescriptionId
                new ShippingAddressRequest("Nguyễn Văn A", "0901234567", "123 Nguyễn Văn Cừ, Q5"),
                "Cần giao gấp"
        );

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(checkoutReq)
                .when()
                .post("/api/v1/orders/checkout")
                .then()
                .statusCode(400)
                .body("code", equalTo("PRESCRIPTION_REQUIRED"))
                .body("details", hasItem("Amoxicillin 500mg"));
    }

    // =========================================================================================
    // Ca 6: Checkout đơn hàng có thuốc kê đơn ETC nhưng đơn thuốc không hợp lệ/hết hạn -> 422
    // =========================================================================================
    @Test
    @Order(6)
    void testCase06_CheckoutETCWithInvalidPrescription_FailsWith422() {
        String token = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");

        CheckoutRequest checkoutReq = new CheckoutRequest(
                INVALID_PRESCRIPTION_ID,
                new ShippingAddressRequest("Nguyễn Văn A", "0901234567", "123 Nguyễn Văn Cừ, Q5"),
                "Đơn thuốc hết hạn"
        );

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(checkoutReq)
                .when()
                .post("/api/v1/orders/checkout")
                .then()
                .statusCode(422)
                .body("code", equalTo("PRESCRIPTION_INVALID"));
    }

    // =========================================================================================
    // Ca 7: Checkout đơn hàng có thuốc kê đơn ETC với prescriptionId hợp lệ -> 201 Created
    // =========================================================================================
    @Test
    @Order(7)
    void testCase07_CheckoutETCWithValidPrescription_Success() {
        String token = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");

        CheckoutRequest checkoutReq = new CheckoutRequest(
                VALID_PRESCRIPTION_ID,
                new ShippingAddressRequest("Nguyễn Văn A", "0901234567", "123 Nguyễn Văn Cừ, Q5"),
                "Đơn thuốc đã được duyệt"
        );

        etcOrderId = given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "idemp-etc-order-002")
                .contentType(ContentType.JSON)
                .body(checkoutReq)
                .when()
                .post("/api/v1/orders/checkout")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("status", equalTo("CHO_XU_LY"))
                .body("prescriptionId", equalTo(VALID_PRESCRIPTION_ID.toString()))
                .body("totalAmount", equalTo(120000.0f))
                .extract().path("id");
    }

    // =========================================================================================
    // Ca 8: Kiểm tra tính Idempotent: Gửi lại request checkout với cùng header "Idempotency-Key" -> Trả đơn cũ
    // =========================================================================================
    @Test
    @Order(8)
    void testCase08_IdempotentCheckout_ReturnsSameOrder() {
        String token = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");

        CheckoutRequest checkoutReq = new CheckoutRequest(
                null,
                new ShippingAddressRequest("Nguyễn Văn A", "0901234567", "123 Nguyễn Văn Cừ, Q5"),
                "Gọi lại cùng key"
        );

        given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "idemp-otc-order-001") // Key của Ca 4
                .contentType(ContentType.JSON)
                .body(checkoutReq)
                .when()
                .post("/api/v1/orders/checkout")
                .then()
                .statusCode(201)
                .body("id", equalTo(otcOrderId))
                .body("idempotencyKey", equalTo("idemp-otc-order-001"));
    }

    // =========================================================================================
    // Ca 9: Bảo mật & Phân quyền: Khách hàng B cố xem đơn Khách A -> 403 Forbidden. Dược sĩ/Admin -> 200 OK
    // =========================================================================================
    @Test
    @Order(9)
    void testCase09_SecurityAndRolePermissions() {
        String tokenCustomerA = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");
        String tokenCustomerB = generateToken(CUSTOMER_B_ID, "KHACH_HANG", "Trần Văn B");
        String tokenPharmacist = generateToken(PHARMACIST_ID, "DUOC_SI", "DS. Trần Thị Dược");
        String tokenAdmin      = generateToken(ADMIN_ID, "ADMIN", "Quản trị viên Admin");

        // 1. Khách B xem đơn của Khách A -> 403 Forbidden
        given()
                .header("Authorization", "Bearer " + tokenCustomerB)
                .when()
                .get("/api/v1/orders/" + otcOrderId)
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));

        // 2. Khách A xem đơn của chính mình -> 200 OK
        given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .when()
                .get("/api/v1/orders/" + otcOrderId)
                .then()
                .statusCode(200)
                .body("id", equalTo(otcOrderId));

        // 3. Dược sĩ xem đơn của Khách A -> 200 OK
        given()
                .header("Authorization", "Bearer " + tokenPharmacist)
                .when()
                .get("/api/v1/orders/" + otcOrderId)
                .then()
                .statusCode(200)
                .body("id", equalTo(otcOrderId));

        // 4. Admin xem đơn của Khách A -> 200 OK
        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .when()
                .get("/api/v1/orders/" + otcOrderId)
                .then()
                .statusCode(200)
                .body("id", equalTo(otcOrderId));

        // 5. Khách A xem danh sách đơn hàng của mình -> Có ít nhất 2 đơn (OTC và ETC)
        given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .when()
                .get("/api/v1/orders/my-orders")
                .then()
                .statusCode(200)
                .body("totalElements", greaterThanOrEqualTo(2));
    }

    // =========================================================================================
    // Ca 10: State Machine - Bước nhảy hợp lệ: Dược sĩ chuyển sang DANG_GIAO, Admin chuyển sang HOAN_THANH
    // =========================================================================================
    @Test
    @Order(10)
    void testCase10_ValidStatusTransitions() {
        String tokenPharmacist = generateToken(PHARMACIST_ID, "DUOC_SI", "DS. Trần Thị Dược");
        String tokenAdmin      = generateToken(ADMIN_ID, "ADMIN", "Quản trị viên Admin");

        // 1. Dược sĩ chuyển đơn otcOrderId từ CHO_XU_LY -> DANG_GIAO
        UpdateOrderStatusRequest reqToShipping = new UpdateOrderStatusRequest(
                OrderStatus.DANG_GIAO,
                "Dược sĩ đóng gói và bàn giao bưu tá Viettel Post"
        );

        given()
                .header("Authorization", "Bearer " + tokenPharmacist)
                .contentType(ContentType.JSON)
                .body(reqToShipping)
                .when()
                .put("/api/v1/orders/" + otcOrderId + "/status")
                .then()
                .statusCode(200)
                .body("id", equalTo(otcOrderId))
                .body("status", equalTo("DANG_GIAO"));

        // 2. Admin chuyển đơn otcOrderId từ DANG_GIAO -> HOAN_THANH
        UpdateOrderStatusRequest reqToDelivered = new UpdateOrderStatusRequest(
                OrderStatus.HOAN_THANH,
                "Khách hàng đã nhận thuốc và ký biên bản giao nhận"
        );

        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(ContentType.JSON)
                .body(reqToDelivered)
                .when()
                .put("/api/v1/orders/" + otcOrderId + "/status")
                .then()
                .statusCode(200)
                .body("id", equalTo(otcOrderId))
                .body("status", equalTo("HOAN_THANH"));
    }

    // =========================================================================================
    // Ca 11: State Machine - Chặn bước nhảy sai quy tắc (400) & Khách hàng không có quyền cập nhật (403)
    // =========================================================================================
    @Test
    @Order(11)
    void testCase11_InvalidStatusTransitions_Blocked() {
        String tokenPharmacist = generateToken(PHARMACIST_ID, "DUOC_SI", "DS. Trần Thị Dược");
        String tokenCustomerA  = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");

        // 1. Nhảy cóc từ CHO_XU_LY -> HOAN_THANH trên etcOrderId -> Bị chặn 400
        UpdateOrderStatusRequest reqSkip = new UpdateOrderStatusRequest(
                OrderStatus.HOAN_THANH,
                "Nhảy cóc từ chờ xử lý sang hoàn thành"
        );

        given()
                .header("Authorization", "Bearer " + tokenPharmacist)
                .contentType(ContentType.JSON)
                .body(reqSkip)
                .when()
                .put("/api/v1/orders/" + etcOrderId + "/status")
                .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_STATUS_TRANSITION"));

        // 2. Cập nhật cùng trạng thái CHO_XU_LY -> CHO_XU_LY -> Bị chặn 400
        UpdateOrderStatusRequest reqSame = new UpdateOrderStatusRequest(
                OrderStatus.CHO_XU_LY,
                "Cập nhật trùng trạng thái"
        );

        given()
                .header("Authorization", "Bearer " + tokenPharmacist)
                .contentType(ContentType.JSON)
                .body(reqSame)
                .when()
                .put("/api/v1/orders/" + etcOrderId + "/status")
                .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_STATUS_TRANSITION"));

        // 3. Khách hàng gọi API cập nhật trạng thái -> 403 Forbidden
        given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .contentType(ContentType.JSON)
                .body(reqSkip)
                .when()
                .put("/api/v1/orders/" + etcOrderId + "/status")
                .then()
                .statusCode(403);
    }

    // =========================================================================================
    // Ca 12: Khách hàng hủy đơn hàng của chính mình khi đơn đang CHO_XU_LY -> 200 DA_HUY
    // =========================================================================================
    @Test
    @Order(12)
    void testCase12_CustomerCancelOrder_Success() {
        String tokenCustomerA = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");

        CancelOrderRequest cancelReq = new CancelOrderRequest("Bác sĩ yêu cầu đổi đơn thuốc khác");

        // Khách A hủy đơn etcOrderId đang CHO_XU_LY
        given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .contentType(ContentType.JSON)
                .body(cancelReq)
                .when()
                .post("/api/v1/orders/" + etcOrderId + "/cancel")
                .then()
                .statusCode(200)
                .body("id", equalTo(etcOrderId))
                .body("status", equalTo("DA_HUY"));

        // Hủy lại đơn đã hủy -> 400 INVALID_STATUS_TRANSITION
        given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .contentType(ContentType.JSON)
                .body(cancelReq)
                .when()
                .post("/api/v1/orders/" + etcOrderId + "/cancel")
                .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_STATUS_TRANSITION"));
    }

    // =========================================================================================
    // Ca 13: Chặn hủy đơn khi đơn không ở CHO_XU_LY (400) & Khách B cố hủy đơn Khách A (403)
    // =========================================================================================
    @Test
    @Order(13)
    void testCase13_CancelOrderInvalidStatusAndForbidden() {
        String tokenCustomerA = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");
        String tokenCustomerB = generateToken(CUSTOMER_B_ID, "KHACH_HANG", "Trần Văn B");

        CancelOrderRequest cancelReq = new CancelOrderRequest("Muốn hủy đơn hàng này");

        // 1. Đơn otcOrderId đã ở HOAN_THANH, không thể hủy -> 400 INVALID_STATUS_TRANSITION
        given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .contentType(ContentType.JSON)
                .body(cancelReq)
                .when()
                .post("/api/v1/orders/" + otcOrderId + "/cancel")
                .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_STATUS_TRANSITION"));

        // 2. Khách B cố hủy đơn otcOrderId của Khách A -> 403 Forbidden
        given()
                .header("Authorization", "Bearer " + tokenCustomerB)
                .contentType(ContentType.JSON)
                .body(cancelReq)
                .when()
                .post("/api/v1/orders/" + otcOrderId + "/cancel")
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    // =========================================================================================
    // Ca 14: Yêu cầu hoàn trả: Đơn HOAN_THANH được hoàn trả (200), đơn DA_HUY không được hoàn trả (400)
    // =========================================================================================
    @Test
    @Order(14)
    void testCase14_RefundOrder() {
        String tokenCustomerA = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");
        String tokenCustomerB = generateToken(CUSTOMER_B_ID, "KHACH_HANG", "Trần Văn B");

        RefundOrderRequest refundReq = new RefundOrderRequest("Sản phẩm bị rách hộp trong lúc vận chuyển");

        // 1. Khách B cố yêu cầu hoàn trả đơn của Khách A -> 403 Forbidden
        given()
                .header("Authorization", "Bearer " + tokenCustomerB)
                .contentType(ContentType.JSON)
                .body(refundReq)
                .when()
                .post("/api/v1/orders/" + otcOrderId + "/refund")
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));

        // 2. Khách A yêu cầu hoàn trả đơn etcOrderId (đang DA_HUY) -> 400 INVALID_STATUS_TRANSITION
        given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .contentType(ContentType.JSON)
                .body(refundReq)
                .when()
                .post("/api/v1/orders/" + etcOrderId + "/refund")
                .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_STATUS_TRANSITION"));

        // 3. Khách A yêu cầu hoàn trả đơn otcOrderId (đang HOAN_THANH) -> 200 HOAN_TRA
        given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .contentType(ContentType.JSON)
                .body(refundReq)
                .when()
                .post("/api/v1/orders/" + otcOrderId + "/refund")
                .then()
                .statusCode(200)
                .body("id", equalTo(otcOrderId))
                .body("status", equalTo("HOAN_TRA"));
    }

    // =========================================================================================
    // Ca 15: Tra cứu lịch sử thay đổi trạng thái (Audit Trail)
    // =========================================================================================
    @Test
    @Order(15)
    void testCase15_GetOrderStatusHistory() {
        String tokenCustomerA = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");
        String tokenCustomerB = generateToken(CUSTOMER_B_ID, "KHACH_HANG", "Trần Văn B");
        String tokenPharmacist = generateToken(PHARMACIST_ID, "DUOC_SI", "DS. Trần Thị Dược");

        // 1. Khách B cố xem lịch sử đơn của Khách A -> 403 Forbidden
        given()
                .header("Authorization", "Bearer " + tokenCustomerB)
                .when()
                .get("/api/v1/orders/" + otcOrderId + "/history")
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));

        // 2. Khách A xem lịch sử đơn của mình -> 200 OK, gồm đúng 4 bước chuyển
        given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .when()
                .get("/api/v1/orders/" + otcOrderId + "/history")
                .then()
                .statusCode(200)
                .body("size()", equalTo(4))
                .body("[0].newStatus", equalTo("CHO_XU_LY"))
                .body("[1].oldStatus", equalTo("CHO_XU_LY"))
                .body("[1].newStatus", equalTo("DANG_GIAO"))
                .body("[2].oldStatus", equalTo("DANG_GIAO"))
                .body("[2].newStatus", equalTo("HOAN_THANH"))
                .body("[3].oldStatus", equalTo("HOAN_THANH"))
                .body("[3].newStatus", equalTo("HOAN_TRA"));

        // 3. Dược sĩ xem lịch sử đơn Khách A -> 200 OK
        given()
                .header("Authorization", "Bearer " + tokenPharmacist)
                .when()
                .get("/api/v1/orders/" + otcOrderId + "/history")
                .then()
                .statusCode(200)
                .body("size()", equalTo(4));
    }

    // =========================================================================================
    // Ca 16: Kafka Consumer xử lý payment.completed & payment.failed kèm Idempotent deduplication
    // =========================================================================================
    @Test
    @Order(16)
    void testCase16_PaymentKafkaConsumer_Idempotent() throws Exception {
        String tokenCustomerA = generateToken(CUSTOMER_A_ID, "KHACH_HANG", "Nguyễn Văn A");

        // 1. Đặt 1 đơn hàng mới để test thanh toán (xóa sạch giỏ trước khi thêm)
        cartRedisService.clearCart(UUID.fromString(CUSTOMER_A_ID));
        given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .contentType(ContentType.JSON)
                .body(new AddToCartRequest(OTC_MED_ID, 1))
                .when()
                .post("/api/v1/cart/items")
                .then()
                .statusCode(200);

        CheckoutRequest checkoutReq = new CheckoutRequest(
                null,
                new ShippingAddressRequest("Nguyễn Văn A", "0901234567", "123 Nguyễn Văn Cừ, Q5"),
                "Đơn thử nghiệm thanh toán"
        );

        String paymentOrderId = given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .header("Idempotency-Key", "idemp-payment-test-001")
                .contentType(ContentType.JSON)
                .body(checkoutReq)
                .when()
                .post("/api/v1/orders/checkout")
                .then()
                .statusCode(201)
                .body("status", equalTo("CHO_XU_LY"))
                .extract().path("id");

        UUID orderUuid = UUID.fromString(paymentOrderId);
        UUID completedEventId = UUID.randomUUID();

        // 2. Nhận event payment.completed
        PaymentCompletedEvent completedEvent = new PaymentCompletedEvent(
                completedEventId,
                "payment.completed",
                Instant.now(),
                "corr-pay-001",
                UUID.randomUUID(),
                orderUuid,
                UUID.fromString(CUSTOMER_A_ID),
                "khachhang@pharmacy.local",
                BigDecimal.valueOf(50000.00),
                "VNPAY",
                "VNP12345678"
        );

        String completedJson = objectMapper.writeValueAsString(completedEvent);
        paymentConsumer.consumePaymentCompleted(completedJson);

        // Kiểm tra lịch sử đơn hàng có bản ghi thanh toán thành công
        given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .when()
                .get("/api/v1/orders/" + paymentOrderId + "/history")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("[1].reason", org.hamcrest.Matchers.containsString("Thanh toán thành công qua VNPAY"));

        // 3. Gửi lại cùng eventId -> Idempotent bỏ qua, lịch sử không tăng thêm
        paymentConsumer.consumePaymentCompleted(completedJson);

        given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .when()
                .get("/api/v1/orders/" + paymentOrderId + "/history")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2));

        // 4. Nhận event payment.failed -> Tự động hủy đơn hàng (chuyển sang DA_HUY)
        UUID failedEventId = UUID.randomUUID();
        PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                failedEventId,
                "payment.failed",
                Instant.now(),
                "corr-pay-002",
                UUID.randomUUID(),
                orderUuid,
                UUID.fromString(CUSTOMER_A_ID),
                "khachhang@pharmacy.local",
                BigDecimal.valueOf(50000.00),
                "VNPAY",
                "51",
                "Tài khoản không đủ số dư để giao dịch"
        );

        String failedJson = objectMapper.writeValueAsString(failedEvent);
        paymentConsumer.consumePaymentFailed(failedJson);

        // Kiểm tra đơn hàng đã tự động chuyển sang DA_HUY
        given()
                .header("Authorization", "Bearer " + tokenCustomerA)
                .when()
                .get("/api/v1/orders/" + paymentOrderId)
                .then()
                .statusCode(200)
                .body("status", equalTo("DA_HUY"));

        // 5. Gửi lại cùng event payment.failed -> Idempotent bỏ qua
        paymentConsumer.consumePaymentFailed(failedJson);

        // Xác nhận processed_events đã lưu cả 2 event ID
        org.junit.jupiter.api.Assertions.assertTrue(processedEventRepo.isEventProcessed(completedEventId));
        org.junit.jupiter.api.Assertions.assertTrue(processedEventRepo.isEventProcessed(failedEventId));
    }
}

