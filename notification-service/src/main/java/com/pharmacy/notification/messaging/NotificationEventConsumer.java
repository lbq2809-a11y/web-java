
package com.pharmacy.notification.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.pharmacy.notification.document.EmailLogDocument;
import com.pharmacy.notification.service.EmailService;
import com.pharmacy.notification.service.EmailTemplateService;
import com.pharmacy.notification.service.NotificationService;

import io.smallrye.reactive.messaging.annotations.Blocking;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@ApplicationScoped
public class NotificationEventConsumer {

    private static final Logger LOG =
            Logger.getLogger(NotificationEventConsumer.class.getName());

    @Inject
    ObjectMapper objectMapper;

    @Inject
    NotificationService notificationService;

    @Inject
    EmailService emailService;

    @Inject
    EmailTemplateService emailTemplateService;

    @ConfigProperty(name = "notification.staff-email")
    String staffEmail;

    @ConfigProperty(
        name = "notification.password-reset-url",
        defaultValue = "UNCONFIGURED"
)
String passwordResetUrl;

    // =========================================================
    // 1. ORDER CREATED
    // =========================================================

    @Incoming("order-created")
    @Blocking
    public void onOrderCreated(String payload) {

        JsonNode event = readEvent(payload);
        String eventId = requiredText(event, "eventId");

        String customerId = text(event, "customerId", null);
        String customerEmail = text(event, "customerEmail", null);
        String customerName = text(event, "customerName", "Quý khách");

        String orderId = text(event, "orderId", "Không xác định");
        String status = text(event, "status", "CHO_XU_LY");

        BigDecimal totalAmount = decimal(event, "totalAmount");

        createUserNotification(
                eventId,
                customerId,
                "ORDER_CREATED",
                "Đặt hàng thành công",
                "Đơn hàng " + orderId
                        + " đã được tiếp nhận. Trạng thái: " + status
        );

        List<Map<String, Object>> items = event.hasNonNull("items")
                ? objectMapper.convertValue(
                        event.get("items"),
                        new TypeReference<List<Map<String, Object>>>() {}
                )
                : List.of();

        String html = emailTemplateService.renderOrderConfirmation(
                customerName,
                orderId,
                status,
                totalAmount,
                items
        );

        sendEmail(
                eventId,
                customerEmail,
                "Xác nhận đơn hàng " + orderId,
                "order-confirmation",
                html
        );

        LOG.info("Processed order.created, eventId=" + eventId);
    }

    // =========================================================
    // 2. ORDER STATUS CHANGED
    // =========================================================

    @Incoming("order-status-changed")
    @Blocking
    public void onOrderStatusChanged(String payload) {

        JsonNode event = readEvent(payload);
        String eventId = requiredText(event, "eventId");

        String customerId = text(event, "customerId", null);
        String customerEmail = text(event, "customerEmail", null);
        String customerName = text(event, "customerName", "Quý khách");

        String orderId = text(event, "orderId", "Không xác định");
        String oldStatus = text(event, "oldStatus", "");
        String newStatus = text(event, "newStatus", "ĐÃ CẬP NHẬT");
        String reason = text(
                event,
                "reason",
                "Đơn hàng đã được cập nhật trạng thái."
        );

        createUserNotification(
                eventId,
                customerId,
                "ORDER_STATUS_CHANGED",
                "Cập nhật trạng thái đơn hàng",
                "Đơn hàng " + orderId
                        + " thay đổi từ " + oldStatus
                        + " sang " + newStatus
                        + ". " + reason
        );

        String html = emailTemplateService.renderOrderStatus(
                customerName,
                orderId,
                newStatus,
                reason
        );

        sendEmail(
                eventId,
                customerEmail,
                "Cập nhật đơn hàng " + orderId,
                "order-status",
                html
        );

        LOG.info("Processed order.status-changed, eventId=" + eventId);
    }

    // =========================================================
    // 3. PAYMENT COMPLETED
    // =========================================================

    @Incoming("payment-completed")
    @Blocking
    public void onPaymentCompleted(String payload) {

        JsonNode event = readEvent(payload);
        String eventId = requiredText(event, "eventId");

        String customerId = text(event, "customerId", null);
        String customerEmail = text(event, "customerEmail", null);

        String orderId = text(event, "orderId", "Không xác định");
        String paymentId = text(event, "paymentId", "Không xác định");
        String paymentMethod = text(event, "paymentMethod", "Không xác định");

        BigDecimal amount = decimal(event, "amount");

        createUserNotification(
                eventId,
                customerId,
                "PAYMENT_COMPLETED",
                "Thanh toán thành công",
                "Thanh toán cho đơn hàng " + orderId
                        + " thành công. Số tiền: " + amount + " VNĐ."
        );

        String html = emailTemplateService.renderPaymentCompleted(
                "Quý khách",
                orderId,
                amount,
                paymentId,
                paymentMethod
        );

        sendEmail(
                eventId,
                customerEmail,
                "Xác nhận thanh toán đơn hàng " + orderId,
                "payment-completed",
                html
        );

        LOG.info("Processed payment.completed, eventId=" + eventId);
    }

    // =========================================================
    // 4. PAYMENT FAILED
    // =========================================================

    @Incoming("payment-failed")
    @Blocking
    public void onPaymentFailed(String payload) {

        JsonNode event = readEvent(payload);
        String eventId = requiredText(event, "eventId");

        String customerId = text(event, "customerId", null);
        String customerEmail = text(event, "customerEmail", null);

        String orderId = text(event, "orderId", "Không xác định");
        String reason = text(
                event,
                "reason",
                "Giao dịch thanh toán không thành công."
        );

        createUserNotification(
                eventId,
                customerId,
                "PAYMENT_FAILED",
                "Thanh toán không thành công",
                "Thanh toán đơn hàng " + orderId + " thất bại. " + reason
        );

        // Tận dụng template cập nhật trạng thái đơn hàng.
        String html = emailTemplateService.renderOrderStatus(
                "Quý khách",
                orderId,
                "THANH_TOAN_LOI",
                reason
        );

        sendEmail(
                eventId,
                customerEmail,
                "Thanh toán đơn hàng " + orderId + " thất bại",
                "payment-failed",
                html
        );

        LOG.info("Processed payment.failed, eventId=" + eventId);
    }

    // =========================================================
    // 5. PRESCRIPTION REVIEWED
    // =========================================================

    @Incoming("prescription-reviewed")
    @Blocking
    public void onPrescriptionReviewed(String payload) {

        JsonNode event = readEvent(payload);
        String eventId = requiredText(event, "eventId");

        String customerId = text(event, "customerId", null);
        String customerEmail = text(event, "customerEmail", null);
        String customerName = text(event, "customerName", "Quý khách");

        String prescriptionId = text(
                event,
                "prescriptionId",
                "Không xác định"
        );

        String status = text(event, "status", "ĐÃ CẬP NHẬT");
        String rejectionReason = text(
                event,
                "rejectionReason",
                "Đơn thuốc đã được dược sĩ kiểm tra."
        );

        String pharmacistName = "Đội ngũ dược sĩ Pharmacy";

        createUserNotification(
                eventId,
                customerId,
                "PRESCRIPTION_REVIEWED",
                "Kết quả duyệt đơn thuốc",
                "Đơn thuốc " + prescriptionId
                        + " có kết quả: " + status
                        + ". " + rejectionReason
        );

        String html = emailTemplateService.renderPrescriptionReviewed(
                customerName,
                prescriptionId,
                status,
                pharmacistName,
                rejectionReason
        );

        sendEmail(
                eventId,
                customerEmail,
                "Kết quả duyệt đơn thuốc " + prescriptionId,
                "prescription-reviewed",
                html
        );

        LOG.info("Processed prescription.reviewed, eventId=" + eventId);
    }

    // =========================================================
    // 6. PASSWORD RESET REQUESTED
    // =========================================================

    @Incoming("password-reset-requested")
    @Blocking
    public void onPasswordResetRequested(String payload) {

        JsonNode event = readEvent(payload);
        String eventId = requiredText(event, "eventId");

        String userId = text(event, "userId", null);
        String email = text(event, "email", null);
        String fullName = text(event, "fullName", "Quý khách");

        String resetToken = requiredText(event, "resetToken");
        int expiresInMinutes = integer(event, "expiresInMinutes", 15);

        // Không lưu resetToken trong notification hoặc log.
        createUserNotification(
                eventId,
                userId,
                "PASSWORD_RESET_REQUESTED",
                "Yêu cầu khôi phục mật khẩu",
                "Hệ thống đã nhận yêu cầu khôi phục mật khẩu. "
                        + "Vui lòng kiểm tra email của bạn."
        );

        String resetUrl = buildPasswordResetUrl(resetToken);

        if (resetUrl == null) {
            LOG.warning(
                    "Skipped password reset email because "
                            + "PASSWORD_RESET_URL is not configured. "
                            + "eventId=" + eventId
            );
            return;
        }

        String html = emailTemplateService.renderPasswordReset(
                fullName,
                resetUrl,
                expiresInMinutes
        );

        sendEmail(
                eventId,
                email,
                "Khôi phục mật khẩu Pharmacy",
                "password-reset",
                html
        );

        LOG.info("Processed password-reset event, eventId=" + eventId);
    }

    // =========================================================
    // 7. INVENTORY LOW STOCK
    // =========================================================

    @Incoming("inventory-low-stock")
    @Blocking
    public void onInventoryLowStock(String payload) {

        JsonNode event = readEvent(payload);
        String eventId = requiredText(event, "eventId");

        String medicineName = text(
                event,
                "medicineName",
                "Không xác định"
        );

        int availableQuantity = integer(event, "availableQuantity", 0);
        int threshold = integer(event, "threshold", 0);

        String html = emailTemplateService.renderInventoryLowStock(
                medicineName,
                availableQuantity,
                threshold
        );

        // Event không có userId: gửi cảnh báo tới email nhân viên.
        sendEmail(
                eventId,
                staffEmail,
                "Cảnh báo tồn kho thấp: " + medicineName,
                "inventory-low-stock",
                html
        );

        LOG.info("Processed inventory.low-stock, eventId=" + eventId);
    }

    // =========================================================
    // 8. INVENTORY NEAR EXPIRY
    // =========================================================

    @Incoming("inventory-near-expiry")
    @Blocking
    public void onInventoryNearExpiry(String payload) {

        JsonNode event = readEvent(payload);
        String eventId = requiredText(event, "eventId");

        String medicineName = text(
                event,
                "medicineName",
                "Không xác định"
        );

        String batchNumber = text(event, "batchNumber", "Không xác định");
        String expiryDate = text(event, "expiryDate", "Không xác định");

        int quantity = integer(event, "quantity", 0);
        int daysLeft = integer(event, "daysLeft", 0);

        String html = emailTemplateService.renderInventoryNearExpiry(
                medicineName,
                batchNumber,
                expiryDate,
                quantity,
                daysLeft
        );

        // Event không có userId: gửi cảnh báo tới email nhân viên.
        sendEmail(
                eventId,
                staffEmail,
                "Cảnh báo thuốc sắp hết hạn: " + medicineName,
                "inventory-near-expiry",
                html
        );

        LOG.info("Processed inventory.near-expiry, eventId=" + eventId);
    }

    // =========================================================
    // COMMON METHODS
    // =========================================================

    private JsonNode readEvent(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Kafka payload rỗng");
        }

        try {
            JsonNode event = objectMapper.readTree(payload);

            if (event == null || !event.isObject()) {
                throw new IllegalArgumentException(
                        "Kafka payload phải là JSON object"
                );
            }

            requiredText(event, "eventId");
            return event;

        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Kafka payload không đúng định dạng JSON",
                    exception
            );
        }
    }

    private String requiredText(JsonNode event, String field) {
        String value = text(event, field, null);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Thiếu trường bắt buộc: " + field
            );
        }

        return value;
    }

    private String text(
            JsonNode event,
            String field,
            String defaultValue
    ) {
        JsonNode value = event.get(field);

        if (value == null || value.isNull()) {
            return defaultValue;
        }

        String result = value.asText();

        return result.isBlank() ? defaultValue : result;
    }

    private BigDecimal decimal(JsonNode event, String field) {
        JsonNode value = event.get(field);

        if (value == null || value.isNull()) {
            return BigDecimal.ZERO;
        }

        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Giá trị số không hợp lệ: " + field,
                    exception
            );
        }
    }

    private int integer(
            JsonNode event,
            String field,
            int defaultValue
    ) {
        JsonNode value = event.get(field);

        if (value == null || value.isNull()) {
            return defaultValue;
        }

        if (!value.canConvertToInt()) {
            throw new IllegalArgumentException(
                    "Giá trị số nguyên không hợp lệ: " + field
            );
        }

        return value.asInt();
    }

    private void createUserNotification(
            String eventId,
            String userId,
            String type,
            String title,
            String content
    ) {
        if (userId == null || userId.isBlank()) {
            LOG.warning(
                    "Skip in-app notification because userId is missing. "
                            + "eventId=" + eventId
            );
            return;
        }

        notificationService.createNotification(
                eventId,
                userId,
                type,
                title,
                content
        );
    }

    private void sendEmail(
            String eventId,
            String recipient,
            String subject,
            String templateKey,
            String html
    ) {
        if (recipient == null || recipient.isBlank()) {
            LOG.warning(
                    "Skip email because recipient is missing. "
                            + "eventId=" + eventId
            );
            return;
        }

        EmailLogDocument result = emailService.sendHtmlEmail(
                eventId,
                recipient,
                subject,
                templateKey,
                html
        );

        if ("FAILED".equals(result.status)) {
            LOG.severe(
                    "Email delivery failed after retries. "
                            + "eventId=" + eventId
                            + ", recipient=" + recipient
            );
        }
    }

    private String buildPasswordResetUrl(String resetToken) {
        if (passwordResetUrl == null
        || passwordResetUrl.isBlank()
        || "UNCONFIGURED".equalsIgnoreCase(passwordResetUrl.trim())) {
    return null;
}
        String encodedToken = URLEncoder.encode(
                resetToken,
                StandardCharsets.UTF_8
        );

        String url;

        if (passwordResetUrl.contains("{token}")) {
            url = passwordResetUrl.replace("{token}", encodedToken);
        } else {
            String separator = passwordResetUrl.contains("?") ? "&" : "?";
            url = passwordResetUrl + separator + "token=" + encodedToken;
        }

        URI uri = URI.create(url);

        if (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(
                    "PASSWORD_RESET_URL phải sử dụng HTTP hoặc HTTPS"
            );
        }

        return url;
    }
}
