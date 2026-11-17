package com.pharmacy.payment;

import com.pharmacy.payment.client.OrderServiceClient;
import com.pharmacy.payment.dto.CreatePaymentRequest;
import com.pharmacy.payment.dto.OrderSnapshot;
import com.pharmacy.payment.entity.PaymentMethod;
import com.pharmacy.payment.repository.OutboxRepository;
import com.pharmacy.payment.repository.PaymentRepository;
import com.pharmacy.payment.repository.PaymentTransactionRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
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
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PaymentResourceTest {

    private static final String ISSUER = "https://pharmacy.com/issuer";
    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PHARMACIST_ID = UUID.fromString("22222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @InjectMock
    @RestClient
    OrderServiceClient orderServiceClient;

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    PaymentTransactionRepository transactionRepository;

    @Inject
    OutboxRepository outboxRepository;

    @BeforeEach
    @Transactional
    void cleanDb() {
        outboxRepository.deleteAll();
        transactionRepository.deleteAll();
        paymentRepository.deleteAll();
        Mockito.reset(orderServiceClient);
    }

    private String generateToken(UUID userId, String role) {
        return Jwt.issuer(ISSUER)
                .subject(userId.toString())
                .upn(role.toLowerCase() + "@pharmacy.local")
                .claim("email", role.toLowerCase() + "@pharmacy.local")
                .groups(Set.of(role))
                .expiresIn(Duration.ofHours(1))
                .sign();
    }

    @Test
    @Order(1)
    void testRbacAndValidationErrorFormat() {
        String customerToken = generateToken(CUSTOMER_ID, "KHACH_HANG");

        // Chưa đăng nhập -> 401
        given()
                .contentType(ContentType.JSON)
                .body(new CreatePaymentRequest(UUID.randomUUID(), PaymentMethod.COD))
                .when()
                .post("/api/v1/payments")
                .then()
                .statusCode(401);

        // Khách hàng tự xác nhận COD -> 403 Forbidden
        given()
                .header("Authorization", "Bearer " + customerToken)
                .when()
                .post("/api/v1/payments/" + UUID.randomUUID() + "/confirm-cod")
                .then()
                .statusCode(403);

        // Khách hàng tự xác nhận QR -> 403 Forbidden
        given()
                .header("Authorization", "Bearer " + customerToken)
                .when()
                .post("/api/v1/payments/" + UUID.randomUUID() + "/confirm-qr")
                .then()
                .statusCode(403);

        // Thiếu dữ liệu bắt buộc -> 400 với ErrorResponse chuẩn JSON
        given()
                .header("Authorization", "Bearer " + customerToken)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/v1/payments")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("message", notNullValue())
                .body("details", notNullValue());
    }

    @Test
    @Order(2)
    void testCreateCodGetByOrderIdAndConfirmCod() {
        String customerToken = generateToken(CUSTOMER_ID, "KHACH_HANG");
        String pharmacistToken = generateToken(PHARMACIST_ID, "DUOC_SI");
        UUID orderId = UUID.randomUUID();

        OrderSnapshot snapshot = new OrderSnapshot(
                orderId,
                CUSTOMER_ID,
                "khachhang@pharmacy.local",
                new BigDecimal("150000.00"),
                "CHO_XU_LY"
        );
        Mockito.when(orderServiceClient.getOrder(eq(orderId), anyString(), anyString()))
                .thenReturn(snapshot);

        // 1. Tạo thanh toán COD -> PENDING
        String paymentId = given()
                .header("Authorization", "Bearer " + customerToken)
                .contentType(ContentType.JSON)
                .body(new CreatePaymentRequest(orderId, PaymentMethod.COD))
                .when()
                .post("/api/v1/payments")
                .then()
                .statusCode(200)
                .body("orderId", equalTo(orderId.toString()))
                .body("method", equalTo("COD"))
                .body("status", equalTo("PENDING"))
                .extract().path("id");

        // 2. Tra cứu thanh toán theo orderId -> 200 OK
        given()
                .header("Authorization", "Bearer " + customerToken)
                .when()
                .get("/api/v1/payments/order/" + orderId)
                .then()
                .statusCode(200)
                .body("id", equalTo(paymentId))
                .body("orderId", equalTo(orderId.toString()));

        // 3. Tra cứu orderId chưa có thanh toán -> 404 ErrorResponse chuẩn
        given()
                .header("Authorization", "Bearer " + customerToken)
                .when()
                .get("/api/v1/payments/order/" + UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", equalTo("PAYMENT_NOT_FOUND"));

        // 4. Dược sĩ xác nhận COD -> COMPLETED & tạo outbox event
        given()
                .header("Authorization", "Bearer " + pharmacistToken)
                .when()
                .post("/api/v1/payments/" + paymentId + "/confirm-cod")
                .then()
                .statusCode(200)
                .body("status", equalTo("COMPLETED"));

        assertEquals(1L, outboxRepository.count("topic", "payment.completed"));
    }

    @Test
    @Order(3)
    void testCreateQrConfirmQrAndPublicVnPayIpnSignatureReject() {
        String customerToken = generateToken(CUSTOMER_ID, "KHACH_HANG");
        String pharmacistToken = generateToken(PHARMACIST_ID, "DUOC_SI");
        UUID orderId = UUID.randomUUID();

        OrderSnapshot snapshot = new OrderSnapshot(
                orderId,
                CUSTOMER_ID,
                "khachhang@pharmacy.local",
                new BigDecimal("85000.00"),
                "CHO_XU_LY"
        );
        Mockito.when(orderServiceClient.getOrder(eq(orderId), any(), anyString()))
                .thenReturn(snapshot);

        String qrPaymentId = given()
                .header("Authorization", "Bearer " + customerToken)
                .contentType(ContentType.JSON)
                .body(new CreatePaymentRequest(orderId, PaymentMethod.QR))
                .when()
                .post("/api/v1/payments")
                .then()
                .statusCode(200)
                .body("method", equalTo("QR"))
                .body("status", equalTo("PENDING"))
                .body("qrContent", startsWith("PHARMACY-PAYMENT|"))
                .extract().path("id");

        // Không thể gọi confirm-cod cho giao dịch QR -> 409 Conflict
        given()
                .header("Authorization", "Bearer " + pharmacistToken)
                .when()
                .post("/api/v1/payments/" + qrPaymentId + "/confirm-cod")
                .then()
                .statusCode(409);

        // Dược sĩ xác nhận đã nhận tiền QR -> 200 COMPLETED & tạo OutboxEvent
        given()
                .header("Authorization", "Bearer " + pharmacistToken)
                .when()
                .post("/api/v1/payments/" + qrPaymentId + "/confirm-qr")
                .then()
                .statusCode(200)
                .body("method", equalTo("QR"))
                .body("status", equalTo("COMPLETED"));

        assertEquals(1L, outboxRepository.count("topic", "payment.completed"));

        // Callback VNPay IPN không cần JWT nhưng chữ ký giả bị trả RspCode=97
        given()
                .queryParam("vnp_TxnRef", UUID.randomUUID().toString().replace("-", ""))
                .queryParam("vnp_Amount", "8500000")
                .queryParam("vnp_SecureHash", "invalid-signature")
                .when()
                .get("/api/v1/payments/vnpay-ipn")
                .then()
                .statusCode(200)
                .body("RspCode", equalTo("97"));
    }
}
