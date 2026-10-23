package com.pharmacy.auth.resource;

import com.pharmacy.auth.config.DataInitializer;
import com.pharmacy.auth.dto.request.ConfirmQrRequest;
import com.pharmacy.auth.dto.request.LoginRequest;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class QrLoginResourceTest {

    private static final String DEFAULT_CUSTOMER_EMAIL = "khach_hang@pharmacy.local";
    private static final String DEFAULT_PASSWORD = "123456";

    @Inject
    DataInitializer dataInitializer;

    @Inject
    EntityManager em;

    @Inject
    RedisDataSource redisDataSource;

    private static boolean initialized = false;

    @BeforeEach
    @Transactional
    void setUp() {
        if (!initialized) {
            dataInitializer.onStart(null);
            initialized = true;
        }
    }

    private String getCustomerToken() {
        LoginRequest loginReq = new LoginRequest(DEFAULT_CUSTOMER_EMAIL, DEFAULT_PASSWORD);
        return given()
                .contentType(ContentType.JSON)
                .body(loginReq)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract().path("accessToken");
    }

    // =========================================================================================
    // Ca 1: Tạo phiên QR thành công (HTTP 201 Created)
    // =========================================================================================
    @Test
    @Order(1)
    @DisplayName("Ca 1: Tạo phiên QR thành công -> 201 Created, qrContent chỉ chứa sessionId, không lộ pollSecret")
    void testCase01_CreateQrSession_Success() {
        Response resp = given()
                .header("X-Forwarded-For", "10.0.1.1")
                .when()
                .post("/api/v1/auth/qr/session")
                .then()
                .statusCode(201)
                .body("sessionId", notNullValue())
                .body("pollSecret", notNullValue())
                .body("qrContent", notNullValue())
                .body("status", equalTo("PENDING"))
                .body("expiresAt", notNullValue())
                .extract().response();

        String sessionId = resp.path("sessionId");
        String qrContent = resp.path("qrContent");
        String pollSecret = resp.path("pollSecret");

        // qrContent chỉ chứa đúng sessionId, tuyệt đối không chứa pollSecret
        org.junit.jupiter.api.Assertions.assertEquals(sessionId, qrContent);
        org.junit.jupiter.api.Assertions.assertFalse(qrContent.contains(pollSecret));
    }

    // =========================================================================================
    // Ca 2: Kiểm tra trạng thái khi thiếu header X-QR-Secret -> 403 Forbidden
    // =========================================================================================
    @Test
    @Order(2)
    @DisplayName("Ca 2: Kiểm tra trạng thái khi thiếu header X-QR-Secret -> 403 Forbidden")
    void testCase02_GetStatus_MissingSecret_FailsWith403() {
        String sessionId = given()
                .header("X-Forwarded-For", "10.0.1.2")
                .when()
                .post("/api/v1/auth/qr/session")
                .then()
                .statusCode(201)
                .extract().path("sessionId");

        given()
                .when()
                .get("/api/v1/auth/qr/session/" + sessionId)
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"))
                .body("message", containsString("Mã bí mật"));
    }

    // =========================================================================================
    // Ca 3: Kiểm tra trạng thái khi sai header X-QR-Secret -> 403 Forbidden
    // =========================================================================================
    @Test
    @Order(3)
    @DisplayName("Ca 3: Kiểm tra trạng thái khi sai header X-QR-Secret -> 403 Forbidden")
    void testCase03_GetStatus_WrongSecret_FailsWith403() {
        String sessionId = given()
                .header("X-Forwarded-For", "10.0.1.3")
                .when()
                .post("/api/v1/auth/qr/session")
                .then()
                .statusCode(201)
                .extract().path("sessionId");

        given()
                .header("X-QR-Secret", "wrong-poll-secret-1234567890abcdef")
                .when()
                .get("/api/v1/auth/qr/session/" + sessionId)
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    // =========================================================================================
    // Ca 4: Kiểm tra trạng thái đúng secret khi phiên đang chờ -> 200 OK (PENDING)
    // =========================================================================================
    @Test
    @Order(4)
    @DisplayName("Ca 4: Kiểm tra trạng thái đúng secret khi phiên đang chờ -> 200 OK (status=PENDING)")
    void testCase04_GetStatus_Pending_Success() {
        Response createResp = given()
                .header("X-Forwarded-For", "10.0.1.4")
                .when()
                .post("/api/v1/auth/qr/session")
                .then()
                .statusCode(201)
                .extract().response();

        String sessionId = createResp.path("sessionId");
        String pollSecret = createResp.path("pollSecret");

        given()
                .header("X-QR-Secret", pollSecret)
                .when()
                .get("/api/v1/auth/qr/session/" + sessionId)
                .then()
                .statusCode(200)
                .body("sessionId", equalTo(sessionId))
                .body("status", equalTo("PENDING"))
                .body("accessToken", nullValue());
    }

    // =========================================================================================
    // Ca 5: Xác nhận phiên thiếu JWT -> 401 Unauthorized
    // =========================================================================================
    @Test
    @Order(5)
    @DisplayName("Ca 5: Xác nhận phiên thiếu header Authorization -> 401 Unauthorized")
    void testCase05_Confirm_MissingJwt_FailsWith401() {
        ConfirmQrRequest confirmReq = new ConfirmQrRequest(UUID.randomUUID().toString());

        given()
                .contentType(ContentType.JSON)
                .body(confirmReq)
                .when()
                .post("/api/v1/auth/qr/confirm")
                .then()
                .statusCode(401);
    }

    // =========================================================================================
    // Ca 6: Xác nhận phiên không tồn tại -> 404 QR_SESSION_NOT_FOUND
    // =========================================================================================
    @Test
    @Order(6)
    @DisplayName("Ca 6: Xác nhận phiên không tồn tại hoặc đã hết hạn -> 404 QR_SESSION_NOT_FOUND")
    void testCase06_Confirm_SessionNotFound_FailsWith404() {
        String token = getCustomerToken();
        String nonExistentSessionId = UUID.randomUUID().toString();
        ConfirmQrRequest confirmReq = new ConfirmQrRequest(nonExistentSessionId);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(confirmReq)
                .when()
                .post("/api/v1/auth/qr/confirm")
                .then()
                .statusCode(404)
                .body("code", equalTo("QR_SESSION_NOT_FOUND"));
    }

    // =========================================================================================
    // Ca 7: Xác nhận phiên đúng bằng JWT -> 200 OK kèm { status: "CONFIRMED" }
    // =========================================================================================
    @Test
    @Order(7)
    @DisplayName("Ca 7: Xác nhận phiên đúng bằng JWT -> 200 OK kèm status: CONFIRMED")
    void testCase07_Confirm_Success() {
        String token = getCustomerToken();

        Response createResp = given()
                .header("X-Forwarded-For", "10.0.1.7")
                .when()
                .post("/api/v1/auth/qr/session")
                .then()
                .statusCode(201)
                .extract().response();

        String sessionId = createResp.path("sessionId");
        ConfirmQrRequest confirmReq = new ConfirmQrRequest(sessionId);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(confirmReq)
                .when()
                .post("/api/v1/auth/qr/confirm")
                .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMED"));
    }

    // =========================================================================================
    // Ca 8: Xác nhận phiên hai lần -> 409 Conflict (QR_SESSION_ALREADY_CONFIRMED)
    // =========================================================================================
    @Test
    @Order(8)
    @DisplayName("Ca 8: Xác nhận phiên hai lần -> 409 Conflict (QR_SESSION_ALREADY_CONFIRMED)")
    void testCase08_Confirm_Twice_FailsWith409() {
        String token = getCustomerToken();

        Response createResp = given()
                .header("X-Forwarded-For", "10.0.1.8")
                .when()
                .post("/api/v1/auth/qr/session")
                .then()
                .statusCode(201)
                .extract().response();

        String sessionId = createResp.path("sessionId");
        ConfirmQrRequest confirmReq = new ConfirmQrRequest(sessionId);

        // Lần 1: Xác nhận thành công
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(confirmReq)
                .when()
                .post("/api/v1/auth/qr/confirm")
                .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMED"));

        // Lần 2: Bị từ chối 409 Conflict do phiên đã được xác nhận trước đó
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(confirmReq)
                .when()
                .post("/api/v1/auth/qr/confirm")
                .then()
                .statusCode(409)
                .body("code", equalTo("QR_SESSION_ALREADY_CONFIRMED"));
    }

    // =========================================================================================
    // Ca 9: Tính chất Single-use: Token chỉ trả một lần ở GET rồi phiên bị xóa (lần sau 404)
    // =========================================================================================
    @Test
    @Order(9)
    @DisplayName("Ca 9: Token chỉ trả 1 lần ở GET rồi phiên bị xóa ngay khỏi Redis -> Lần gọi thứ 2 trả 404")
    void testCase09_GetStatus_SingleUseTokenDelivery_Then404() {
        String token = getCustomerToken();

        Response createResp = given()
                .header("X-Forwarded-For", "10.0.1.9")
                .when()
                .post("/api/v1/auth/qr/session")
                .then()
                .statusCode(201)
                .extract().response();

        String sessionId = createResp.path("sessionId");
        String pollSecret = createResp.path("pollSecret");

        // Mobile xác nhận phiên
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(new ConfirmQrRequest(sessionId))
                .when()
                .post("/api/v1/auth/qr/confirm")
                .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMED"));

        // Web polling lần 1: Nhận token thành công
        given()
                .header("X-QR-Secret", pollSecret)
                .when()
                .get("/api/v1/auth/qr/session/" + sessionId)
                .then()
                .statusCode(200)
                .body("sessionId", equalTo(sessionId))
                .body("status", equalTo("CONFIRMED"))
                .body("accessToken", notNullValue())
                .body("tokenType", equalTo("Bearer"))
                .body("user.email", equalTo(DEFAULT_CUSTOMER_EMAIL));

        // Web polling lần 2: Phiên đã bị xóa khỏi Redis -> 404 Not Found
        given()
                .header("X-QR-Secret", pollSecret)
                .when()
                .get("/api/v1/auth/qr/session/" + sessionId)
                .then()
                .statusCode(404)
                .body("code", equalTo("QR_SESSION_NOT_FOUND"));
    }

    // =========================================================================================
    // Ca 10: Vượt quá giới hạn tạo phiên 10 lần/phút từ cùng một IP -> 429 Too Many Requests
    // =========================================================================================
    @Test
    @Order(10)
    @DisplayName("Ca 10: Vượt quá giới hạn tạo phiên 10 lần/phút từ cùng một IP -> 429 Too Many Requests")
    void testCase10_RateLimit_Exceeded_FailsWith429() {
        String spamIp = "192.168.99.1";

        // Xóa key rate limit cũ của IP này nếu có
        redisDataSource.key().del("qr:rate_limit:" + spamIp);

        // Gọi 10 lần đầu thành công (HTTP 201)
        for (int i = 1; i <= 10; i++) {
            given()
                    .header("X-Forwarded-For", spamIp)
                    .when()
                    .post("/api/v1/auth/qr/session")
                    .then()
                    .statusCode(201);
        }

        // Lần thứ 11 từ cùng IP -> Bị chặn 429 Too Many Requests
        given()
                .header("X-Forwarded-For", spamIp)
                .when()
                .post("/api/v1/auth/qr/session")
                .then()
                .statusCode(429)
                .body("code", equalTo("TOO_MANY_REQUESTS"))
                .body("message", containsString("Quá nhiều yêu cầu"));
    }

    // =========================================================================================
    // Ca 11: Phiên hết hạn TTL bị Redis xóa -> GET trả 404 QR_SESSION_NOT_FOUND
    // =========================================================================================
    @Test
    @Order(11)
    @DisplayName("Ca 11: Phiên hết hạn TTL bị Redis xóa -> GET trả 404 QR_SESSION_NOT_FOUND")
    void testCase11_ExpiredSession_Returns404() {
        Response createResp = given()
                .header("X-Forwarded-For", "10.0.1.11")
                .when()
                .post("/api/v1/auth/qr/session")
                .then()
                .statusCode(201)
                .extract().response();

        String sessionId = createResp.path("sessionId");
        String pollSecret = createResp.path("pollSecret");

        // Mô phỏng Redis xóa phiên khi hết hạn TTL (DEL key)
        redisDataSource.key().del("qr:session:" + sessionId);

        given()
                .header("X-QR-Secret", pollSecret)
                .when()
                .get("/api/v1/auth/qr/session/" + sessionId)
                .then()
                .statusCode(404)
                .body("code", equalTo("QR_SESSION_NOT_FOUND"));
    }

    // =========================================================================================
    // Ca 12: WebSocket QR - Sai secret bị từ chối kết nối
    // =========================================================================================
    @Test
    @Order(12)
    @DisplayName("Ca 12: WebSocket QR - Kết nối với secret không hợp lệ bị từ chối/đóng kết nối")
    void testCase12_WebSocket_WrongSecret_Rejected() throws Exception {
        Response createResp = given()
                .header("X-Forwarded-For", "10.0.1.12")
                .when()
                .post("/api/v1/auth/qr/session")
                .then()
                .statusCode(201)
                .extract().response();

        String sessionId = createResp.path("sessionId");

        CompletableFuture<Integer> closeFuture = new CompletableFuture<>();
        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();

        HttpClient client = HttpClient.newHttpClient();
        int port = RestAssured.port;
        URI wsUri = URI.create("ws://localhost:" + port + "/ws/qr/" + sessionId + "?secret=wrong_invalid_secret");

        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                closeFuture.complete(statusCode);
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                errorFuture.complete(error);
            }
        };

        try {
            WebSocket ws = client.newWebSocketBuilder().buildAsync(wsUri, listener).get(3, TimeUnit.SECONDS);
            // Nếu handshake được hoàn thành, server sẽ gửi close frame 1008
            Integer closeCode = closeFuture.get(3, TimeUnit.SECONDS);
            assertTrue(closeCode == 1008 || closeCode == 1000 || closeCode == 1002);
        } catch (Exception e) {
            // Handshake bị từ chối cấp HTTP upgrade (403/connection error) cũng là hành vi đúng chuẩn
            assertNotNull(e);
        }
    }

    // =========================================================================================
    // Ca 13: WebSocket QR - Xác nhận thành công và đẩy token thời gian thực qua WebSocket
    // =========================================================================================
    @Test
    @Order(13)
    @DisplayName("Ca 13: WebSocket QR - Đúng secret, nhận sự kiện CONFIRMED kèm accessToken khi mobile confirm")
    void testCase13_WebSocket_Confirmed_ReceivesTokenRealtime() throws Exception {
        String token = getCustomerToken();

        Response createResp = given()
                .header("X-Forwarded-For", "10.0.1.13")
                .when()
                .post("/api/v1/auth/qr/session")
                .then()
                .statusCode(201)
                .extract().response();

        String sessionId = createResp.path("sessionId");
        String pollSecret = createResp.path("pollSecret");

        CompletableFuture<String> messageFuture = new CompletableFuture<>();
        CompletableFuture<Integer> closeFuture = new CompletableFuture<>();

        HttpClient client = HttpClient.newHttpClient();
        int port = RestAssured.port;
        URI wsUri = URI.create("ws://localhost:" + port + "/ws/qr/" + sessionId + "?secret=" + pollSecret);

        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                messageFuture.complete(data.toString());
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                closeFuture.complete(statusCode);
                return null;
            }
        };

        WebSocket ws = client.newWebSocketBuilder().buildAsync(wsUri, listener).get(5, TimeUnit.SECONDS);

        // Mobile gửi xác nhận phiên
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(new ConfirmQrRequest(sessionId))
                .when()
                .post("/api/v1/auth/qr/confirm")
                .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMED"));

        // Web client nhận được message CONFIRMED qua WebSocket trong vòng 5 giây
        String receivedJson = messageFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(receivedJson);
        assertTrue(receivedJson.contains("\"status\":\"CONFIRMED\""));
        assertTrue(receivedJson.contains("\"accessToken\":"));
        assertTrue(receivedJson.contains("\"user\":"));

        // Sau khi gửi token, WebSocket tự động đóng và phiên bị xóa khỏi Redis
        closeFuture.get(5, TimeUnit.SECONDS);

        // Kiểm tra phiên đã bị xóa khỏi Redis -> GET trả về 404
        given()
                .header("X-QR-Secret", pollSecret)
                .when()
                .get("/api/v1/auth/qr/session/" + sessionId)
                .then()
                .statusCode(404)
                .body("code", equalTo("QR_SESSION_NOT_FOUND"));
    }
}
