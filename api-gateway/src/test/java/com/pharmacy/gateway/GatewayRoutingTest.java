package com.pharmacy.gateway;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class GatewayRoutingTest {

    private static Vertx mockVertx;
    private static HttpServer authServer;
    private static HttpServer medicineServer;
    private static HttpServer prescriptionServer;
    private static HttpServer orderServer;
    private static HttpServer paymentServer;

    @BeforeAll
    public static void setupMockServers() throws Exception {
        mockVertx = Vertx.vertx();

        // 1. Mock Medicine Service (Cổng 18081)
        CompletableFuture<Void> medFuture = new CompletableFuture<>();
        medicineServer = mockVertx.createHttpServer();
        medicineServer.requestHandler(req -> {
            String query = req.query();
            if (query != null && query.contains("slow=true")) {
                mockVertx.setTimer(800, timerId -> {
                    req.response()
                        .putHeader("Content-Type", "application/json; charset=UTF-8")
                        .setStatusCode(200)
                        .end("[{\"id\":\"slow\",\"name\":\"Slow Medicine\"}]");
                });
                return;
            }
            String correlationId = req.getHeader("X-Correlation-Id");
            req.response()
                .putHeader("Content-Type", "application/json; charset=UTF-8")
                .putHeader("X-Received-Correlation-Id", correlationId != null ? correlationId : "")
                .putHeader("X-Received-Query", req.query() != null ? req.query() : "")
                .setStatusCode(200)
                .end("[{\"id\":\"6ad23be6c034632667baaa05\",\"name\":\"Paracetamol 500mg\",\"price\":50000.00}]");
        }).listen(18081, ar -> {
            if (ar.succeeded()) medFuture.complete(null);
            else medFuture.completeExceptionally(ar.cause());
        });

        // 2. Mock Auth Service (Cổng 18082)
        CompletableFuture<Void> authFuture = new CompletableFuture<>();
        authServer = mockVertx.createHttpServer();
        authServer.requestHandler(req -> {
            String path = req.path();

            // Endpoint giả lập timeout (delay 800ms > gateway timeout 400ms)
            if (path.contains("/slow")) {
                mockVertx.setTimer(800, timerId -> {
                    req.response()
                        .putHeader("Content-Type", "application/json; charset=UTF-8")
                        .setStatusCode(200)
                        .end("{\"message\":\"slow-response\"}");
                });
                return;
            }

            req.bodyHandler(body -> {
                String roleHeader = req.getHeader("X-Role");
                String userIdHeader = req.getHeader("X-User-Id");
                String custIdHeader = req.getHeader("X-Customer-Id");

                req.response()
                    .putHeader("Content-Type", "application/json; charset=UTF-8")
                    .putHeader("X-Has-Spoofed-Role", String.valueOf(roleHeader != null))
                    .putHeader("X-Has-Spoofed-UserId", String.valueOf(userIdHeader != null))
                    .putHeader("X-Has-Spoofed-CustId", String.valueOf(custIdHeader != null))
                    .setStatusCode(200)
                    .end("{\"token\":\"dummy-access-token\",\"status\":\"SUCCESS\",\"path\":\"" + path + "\"}");
            });
        }).listen(18082, ar -> {
            if (ar.succeeded()) authFuture.complete(null);
            else authFuture.completeExceptionally(ar.cause());
        });

        // 3. Mock Prescription Service (Cổng 18083 - Kiểm tra nhận Multipart Stream & Magic Bytes)
        CompletableFuture<Void> presFuture = new CompletableFuture<>();
        prescriptionServer = mockVertx.createHttpServer();
        prescriptionServer.requestHandler(req -> {
            req.bodyHandler(body -> {
                byte[] bytes = body.getBytes();
                boolean hasJpgMagicBytes = false;
                for (int i = 0; i < bytes.length - 2; i++) {
                    if ((bytes[i] & 0xFF) == 0xFF && (bytes[i + 1] & 0xFF) == 0xD8 && (bytes[i + 2] & 0xFF) == 0xFF) {
                        hasJpgMagicBytes = true;
                        break;
                    }
                }
                req.response()
                    .putHeader("Content-Type", "application/json; charset=UTF-8")
                    .putHeader("X-Has-Jpg-Magic-Bytes", String.valueOf(hasJpgMagicBytes))
                    .setStatusCode(201)
                    .end("{\"prescriptionId\":\"550e8400-e29b-41d4-a716-446655440000\",\"status\":\"CHO_DUYET\"}");
            });
        }).listen(18083, ar -> {
            if (ar.succeeded()) presFuture.complete(null);
            else presFuture.completeExceptionally(ar.cause());
        });

        // 4. Mock Order Service (Cổng 18084 - Kiểm tra nhận Authorization & Idempotency-Key)
        CompletableFuture<Void> orderFuture = new CompletableFuture<>();
        orderServer = mockVertx.createHttpServer();
        orderServer.requestHandler(req -> {
            String auth = req.getHeader("Authorization");
            String idemp = req.getHeader("Idempotency-Key");
            String correlationId = req.getHeader("X-Correlation-Id");
            req.response()
                .putHeader("Content-Type", "application/json; charset=UTF-8")
                .putHeader("X-Received-Auth", auth != null ? auth : "")
                .putHeader("X-Received-Idemp", idemp != null ? idemp : "")
                .putHeader("X-Received-Correlation-Id", correlationId != null ? correlationId : "")
                .setStatusCode(201)
                .end("{\"orderId\":\"11111111-2222-3333-4444-555555555555\",\"status\":\"CHO_XU_LY\"}");
        }).listen(18084, ar -> {
            if (ar.succeeded()) orderFuture.complete(null);
            else orderFuture.completeExceptionally(ar.cause());
        });

        // 5. Mock Payment Service (Cổng 18085 - VNPay IPN, Return & Checkout)
        CompletableFuture<Void> payFuture = new CompletableFuture<>();
        paymentServer = mockVertx.createHttpServer();
        paymentServer.requestHandler(req -> {
            String path = req.path();
            req.response()
                .putHeader("Content-Type", "application/json; charset=UTF-8")
                .setStatusCode(200)
                .end("{\"RspCode\":\"00\",\"Message\":\"Confirm Success\",\"path\":\"" + path + "\"}");
        }).listen(18085, ar -> {
            if (ar.succeeded()) payFuture.complete(null);
            else payFuture.completeExceptionally(ar.cause());
        });

        CompletableFuture.allOf(medFuture, authFuture, presFuture, orderFuture, payFuture).get(10, TimeUnit.SECONDS);
    }

    @AfterAll
    public static void tearDownMockServers() throws Exception {
        CompletableFuture<Void> medClose = new CompletableFuture<>();
        CompletableFuture<Void> authClose = new CompletableFuture<>();
        CompletableFuture<Void> presClose = new CompletableFuture<>();
        CompletableFuture<Void> orderClose = new CompletableFuture<>();
        CompletableFuture<Void> payClose = new CompletableFuture<>();

        if (medicineServer != null) medicineServer.close(ar -> medClose.complete(null)); else medClose.complete(null);
        if (authServer != null) authServer.close(ar -> authClose.complete(null)); else authClose.complete(null);
        if (prescriptionServer != null) prescriptionServer.close(ar -> presClose.complete(null)); else presClose.complete(null);
        if (orderServer != null) orderServer.close(ar -> orderClose.complete(null)); else orderClose.complete(null);
        if (paymentServer != null) paymentServer.close(ar -> payClose.complete(null)); else payClose.complete(null);

        CompletableFuture.allOf(medClose, authClose, presClose, orderClose, payClose).get(5, TimeUnit.SECONDS);
        if (mockVertx != null) {
            mockVertx.close();
        }
    }

    private String createValidToken(String role) {
        return Jwt.issuer("https://pharmacy.com/issuer")
            .upn("test@pharmacy.com")
            .subject(UUID.randomUUID().toString())
            .groups(Set.of(role))
            .claim("role", role)
            .claim("fullName", "Nguyen Van Test")
            .expiresIn(3600)
            .sign();
    }

    @Test
    @DisplayName("Ca 1: Định tuyến GET công khai /api/v1/medicines bảo toàn query params và trả về dữ liệu")
    public void testPublicRouteMedicineForwarding() {
        given()
            .when()
            .get("/api/v1/medicines?page=0&size=10")
            .then()
            .statusCode(200)
            .header("X-Correlation-Id", notNullValue())
            .header("X-Received-Query", equalTo("page=0&size=10"))
            .body("[0].name", equalTo("Paracetamol 500mg"));
    }

    @Test
    @DisplayName("Ca 2: Tự động sinh X-Correlation-Id chuẩn UUID khi client không gửi")
    public void testCorrelationIdGeneratedWhenMissing() {
        given()
            .when()
            .get("/api/v1/medicines")
            .then()
            .statusCode(200)
            .header("X-Correlation-Id", matchesPattern("^[0-9a-fA-F-]{36}$"));
    }

    @Test
    @DisplayName("Ca 3: Chuyển tiếp nguyên vẹn X-Correlation-Id khi client đã gửi lên")
    public void testCorrelationIdPropagatedWhenProvided() {
        String customCorrelationId = "custom-trace-uuid-12345678";
        given()
            .header("X-Correlation-Id", customCorrelationId)
            .when()
            .get("/api/v1/medicines")
            .then()
            .statusCode(200)
            .header("X-Correlation-Id", equalTo(customCorrelationId))
            .header("X-Received-Correlation-Id", equalTo(customCorrelationId));
    }

    @Test
    @DisplayName("Ca 4: Cho phép truy cập Public đối với POST /api/v1/auth/login không cần JWT")
    public void testPublicAuthRouteLoginAllowedWithoutToken() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"khachhang@gmail.com\",\"password\":\"123456\"}")
            .when()
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .body("token", equalTo("dummy-access-token"));
    }

    @Test
    @DisplayName("Ca 5: Route QR - Tạo phiên (POST /qr/session) là công khai không cần JWT")
    public void testQrSessionCreationPublicAllowed() {
        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/v1/auth/qr/session")
            .then()
            .statusCode(200)
            .body("status", equalTo("SUCCESS"));
    }

    @Test
    @DisplayName("Ca 6: Route QR - Kiểm tra trạng thái phiên (GET /qr/session/{id}) là công khai không cần JWT")
    public void testQrSessionStatusCheckPublicAllowed() {
        given()
            .when()
            .get("/api/v1/auth/qr/session/sess-987654")
            .then()
            .statusCode(200)
            .body("status", equalTo("SUCCESS"));
    }

    @Test
    @DisplayName("Ca 7: Route QR - Xác nhận phiên (POST /qr/confirm) BẮT BUỘC cần JWT, chặn 401 nếu thiếu")
    public void testQrConfirmRequiresJwt() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"sessionId\":\"sess-987654\"}")
            .when()
            .post("/api/v1/auth/qr/confirm")
            .then()
            .statusCode(401)
            .body("code", equalTo("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Ca 8: Route QR - Xác nhận phiên (POST /qr/confirm) thành công khi có JWT hợp lệ")
    public void testQrConfirmAllowedWithValidJwt() {
        String token = createValidToken("KHACH_HANG");
        given()
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .body("{\"sessionId\":\"sess-987654\"}")
            .when()
            .post("/api/v1/auth/qr/confirm")
            .then()
            .statusCode(200)
            .body("status", equalTo("SUCCESS"));
    }

    @Test
    @DisplayName("Ca 9: Chống Path Traversal - /api/v1/auth/qr/session/../confirm không lọt qua route công khai (chặn 401)")
    public void testPathTraversalAttemptBlocked() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"sessionId\":\"sess-987654\"}")
            .when()
            .post("/api/v1/auth/qr/session/../confirm")
            .then()
            .statusCode(401)
            .body("code", equalTo("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Ca 10: VNPay Callbacks - IPN và Return URL (/payments/vnpay/**) là công khai không cần JWT")
    public void testVnPayCallbacksPublicAllowed() {
        // VNPay IPN webhook
        given()
            .when()
            .get("/api/v1/payments/vnpay/ipn?vnp_ResponseCode=00&vnp_TxnRef=10001")
            .then()
            .statusCode(200)
            .body("RspCode", equalTo("00"));

        // VNPay Return URL
        given()
            .when()
            .get("/api/v1/payments/vnpay/return?vnp_ResponseCode=00&vnp_TxnRef=10001")
            .then()
            .statusCode(200)
            .body("RspCode", equalTo("00"));
    }

    @Test
    @DisplayName("Ca 11: Các API Payments khác (ví dụ POST /payments) BẮT BUỘC cần JWT, chặn 401 nếu thiếu")
    public void testOtherPaymentRoutesRequireJwt() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"orderId\":\"11111111-2222-3333-4444-555555555555\",\"paymentMethod\":\"VNPAY\"}")
            .when()
            .post("/api/v1/payments")
            .then()
            .statusCode(401)
            .body("code", equalTo("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Ca 12: Chống giả mạo Header - Gateway lọc sạch X-Role, X-User-Id, X-Customer-Id gửi từ client")
    public void testSpoofableInternalHeadersStripped() {
        given()
            .header("X-Role", "ADMIN")
            .header("X-User-Id", "99999999-9999-9999-9999-999999999999")
            .header("X-Customer-Id", "custom-fake-id")
            .contentType(ContentType.JSON)
            .body("{\"email\":\"test@pharmacy.com\",\"password\":\"123456\"}")
            .when()
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .header("X-Has-Spoofed-Role", equalTo("false"))
            .header("X-Has-Spoofed-UserId", equalTo("false"))
            .header("X-Has-Spoofed-CustId", equalTo("false"));
    }

    @Test
    @DisplayName("Ca 13: Chặn 401 Unauthorized đối với Protected Route khi thiếu header Authorization")
    public void testProtectedRouteRejectedWhenMissingToken() {
        given()
            .when()
            .get("/api/v1/cart")
            .then()
            .statusCode(401)
            .header("X-Correlation-Id", notNullValue())
            .body("code", equalTo("UNAUTHORIZED"))
            .body("message", equalTo("Yêu cầu đăng nhập để truy cập tài nguyên này"));
    }

    @Test
    @DisplayName("Ca 14: Chặn 401 Unauthorized khi token JWT giả mạo hoặc sai định dạng")
    public void testProtectedRouteRejectedWhenTokenIsInvalid() {
        given()
            .header("Authorization", "Bearer invalid.malformed.garbage-token")
            .when()
            .get("/api/v1/cart")
            .then()
            .statusCode(401)
            .body("code", equalTo("UNAUTHORIZED"))
            .body("message", equalTo("Token xác thực không hợp lệ hoặc đã hết hạn"));
    }

    @Test
    @DisplayName("Ca 15: Cho phép truy cập khi có JWT hợp lệ và chuyển tiếp trọn vẹn Idempotency-Key")
    public void testProtectedRouteAllowedWithValidTokenAndHeadersPropagated() {
        String validToken = createValidToken("KHACH_HANG");
        String idempotencyKey = UUID.randomUUID().toString();

        given()
            .header("Authorization", "Bearer " + validToken)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(ContentType.JSON)
            .body("{\"note\":\"Giao gio hanh chinh\"}")
            .when()
            .post("/api/v1/orders")
            .then()
            .statusCode(201)
            .header("X-Received-Auth", equalTo("Bearer " + validToken))
            .header("X-Received-Idemp", equalTo(idempotencyKey))
            .body("orderId", equalTo("11111111-2222-3333-4444-555555555555"))
            .body("status", equalTo("CHO_XU_LY"));
    }

    @Test
    @DisplayName("Ca 16: Trả về 503 SERVICE_UNAVAILABLE khi dịch vụ đích backend bị mất kết nối")
    public void testDownstreamServiceUnavailable503() {
        String validToken = createValidToken("KHACH_HANG");

        // Cổng 18086 (notification-service) không có server mock nào chạy -> Connection Refused
        given()
            .header("Authorization", "Bearer " + validToken)
            .when()
            .get("/api/v1/notifications")
            .then()
            .statusCode(503)
            .header("X-Correlation-Id", notNullValue())
            .body("code", equalTo("SERVICE_UNAVAILABLE"))
            .body("message", containsString("Dịch vụ đích tạm thời không khả dụng"));
    }

    @Test
    @DisplayName("Ca 17: Trả về 504 GATEWAY_TIMEOUT khi dịch vụ backend xử lý quá hạn (timeout)")
    public void testBackendTimeoutReturns504GatewayTimeout() {
        // Gọi endpoint có tham số ?slow=true (delay 800ms > timeout cấu hình 400ms)
        given()
            .when()
            .get("/api/v1/medicines?slow=true")
            .then()
            .statusCode(504)
            .header("X-Correlation-Id", notNullValue())
            .body("code", equalTo("GATEWAY_TIMEOUT"))
            .body("message", containsString("Timeout"));
    }

    @Test
    @DisplayName("Ca 18: Stream trực tiếp Multipart Form Data upload ảnh bảo toàn 100% magic bytes nhị phân")
    public void testMultipartFileUploadStreamingPreserved() {
        String validToken = createValidToken("KHACH_HANG");
        byte[] mockJpgBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'};

        given()
            .header("Authorization", "Bearer " + validToken)
            .multiPart("file", "don-thuoc.jpg", mockJpgBytes, "image/jpeg")
            .formParam("diagnosis", "Viem hong cap")
            .when()
            .post("/api/v1/prescriptions")
            .then()
            .statusCode(201)
            .header("X-Has-Jpg-Magic-Bytes", equalTo("true"))
            .body("prescriptionId", equalTo("550e8400-e29b-41d4-a716-446655440000"));
    }

    @Test
    @DisplayName("Ca 19: Xử lý CORS Preflight (OPTIONS request) cho phép gọi từ localhost Frontend")
    public void testCorsPreflightOptionsRequest() {
        given()
            .header("Origin", "http://localhost:3000")
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "Authorization, Content-Type, X-Correlation-Id")
            .when()
            .options("/api/v1/medicines")
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(204)))
            .header("Access-Control-Allow-Origin", anyOf(equalTo("http://localhost:3000"), equalTo("*")));
    }

    @Test
    @DisplayName("Ca 20: OpenAPI & Swagger UI hiển thị đầy đủ danh sách endpoint từ openapi.yaml")
    public void testOpenApiEndpointsExposed() {
        given()
            .when()
            .get("/q/openapi")
            .then()
            .statusCode(200)
            .body(containsString("/api/v1/auth/login"))
            .body(containsString("/api/v1/auth/qr/session"))
            .body(containsString("/api/v1/payments/vnpay/ipn"))
            .body(containsString("/api/v1/medicines"))
            .body(containsString("/api/v1/orders"));
    }
}
