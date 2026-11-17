package com.pharmacy.notification;

import com.pharmacy.notification.messaging.NotificationEventConsumer;
import com.pharmacy.notification.repository.EmailLogRepository;
import com.pharmacy.notification.repository.NotificationRepository;
import com.pharmacy.notification.service.NotificationService;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class NotificationResourceTest {

    private static final String ISSUER = "https://pharmacy.com/issuer";
    private static final String USER_A = "11111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String USER_B = "22222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Inject
    NotificationRepository notificationRepository;

    @Inject
    EmailLogRepository emailLogRepository;

    @Inject
    NotificationService notificationService;

    @Inject
    NotificationEventConsumer eventConsumer;

    @BeforeEach
    void cleanDb() {
        notificationRepository.deleteAll();
        emailLogRepository.deleteAll();
    }

    private String generateToken(String userId, String role) {
        return Jwt.issuer(ISSUER)
                .subject(userId)
                .upn(role.toLowerCase() + "@pharmacy.local")
                .claim("email", role.toLowerCase() + "@pharmacy.local")
                .groups(Set.of(role))
                .expiresIn(Duration.ofHours(1))
                .sign();
    }

    @Test
    void testNotificationsRequiresAuthAndUnreadWorkflow() {
        // Chưa đăng nhập -> 401
        given()
                .when()
                .get("/api/v1/notifications")
                .then()
                .statusCode(401);

        var notif1 = notificationService.createNotification(
                UUID.randomUUID().toString(),
                USER_A,
                "ORDER_CREATED",
                "Đặt hàng thành công",
                "Đơn hàng #1 đã được tiếp nhận."
        );
        notificationService.createNotification(
                UUID.randomUUID().toString(),
                USER_A,
                "PAYMENT_COMPLETED",
                "Thanh toán thành công",
                "Đơn hàng #1 đã thanh toán."
        );
        notificationService.createNotification(
                UUID.randomUUID().toString(),
                USER_B,
                "ORDER_CREATED",
                "Thông báo của B",
                "Chỉ user B nhìn thấy."
        );

        String tokenA = generateToken(USER_A, "KHACH_HANG");
        String tokenB = generateToken(USER_B, "KHACH_HANG");

        // User A lấy danh sách & đếm chưa đọc -> 2
        given()
                .header("Authorization", "Bearer " + tokenA)
                .when()
                .get("/api/v1/notifications")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2));

        given()
                .header("Authorization", "Bearer " + tokenA)
                .when()
                .get("/api/v1/notifications/unread-count")
                .then()
                .statusCode(200)
                .body("unreadCount", equalTo(2));

        // User B không được đánh dấu đã đọc thông báo của User A -> 404
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .put("/api/v1/notifications/" + notif1.id.toHexString() + "/read")
                .then()
                .statusCode(404)
                .body("code", equalTo("NOTIFICATION_NOT_FOUND"));

        // User A đánh dấu đã đọc 1 thông báo -> còn 1 chưa đọc
        given()
                .header("Authorization", "Bearer " + tokenA)
                .when()
                .put("/api/v1/notifications/" + notif1.id.toHexString() + "/read")
                .then()
                .statusCode(200)
                .body("isRead", equalTo(true));

        given()
                .header("Authorization", "Bearer " + tokenA)
                .when()
                .get("/api/v1/notifications/unread-count")
                .then()
                .statusCode(200)
                .body("unreadCount", equalTo(1));

        // User A đánh dấu tất cả đã đọc -> 0 chưa đọc
        given()
                .header("Authorization", "Bearer " + tokenA)
                .when()
                .put("/api/v1/notifications/read-all")
                .then()
                .statusCode(200)
                .body("updatedCount", equalTo(1));

        given()
                .header("Authorization", "Bearer " + tokenA)
                .when()
                .get("/api/v1/notifications/unread-count")
                .then()
                .statusCode(200)
                .body("unreadCount", equalTo(0));
    }

    @Test
    void testKafkaConsumerIdempotencyOnPaymentCompleted() {
        String eventId = UUID.randomUUID().toString();
        String payload = """
                {
                  "eventId": "%s",
                  "eventType": "payment.completed",
                  "orderId": "%s",
                  "paymentId": "%s",
                  "customerId": "%s",
                  "customerEmail": "khach@pharmacy.local",
                  "amount": 150000.00,
                  "paymentMethod": "VNPAY"
                }
                """.formatted(eventId, UUID.randomUUID(), UUID.randomUUID(), USER_A);

        eventConsumer.onPaymentCompleted(payload);
        eventConsumer.onPaymentCompleted(payload);

        assertEquals(1L, notificationRepository.count("userId", USER_A));
        assertEquals(1L, emailLogRepository.count("eventId", eventId));
    }
}
