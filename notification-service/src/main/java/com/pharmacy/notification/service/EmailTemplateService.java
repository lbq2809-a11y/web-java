
package com.pharmacy.notification.service;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Service quản lý và render các mẫu email HTML bằng Qute.
 */
@ApplicationScoped
public class EmailTemplateService {

    @Inject
    @Location("order-confirmation")
    Template orderConfirmation;

    @Inject
    @Location("order-status")
    Template orderStatus;

    @Inject
    @Location("payment-completed")
    Template paymentCompleted;

    @Inject
    @Location("password-reset")
    Template passwordReset;

    @Inject
    @Location("prescription-reviewed")
    Template prescriptionReviewed;

    @Inject
    @Location("inventory-low-stock")
    Template inventoryLowStock;

    @Inject
    @Location("inventory-near-expiry")
    Template inventoryNearExpiry;

    /**
     * Render email xác nhận đơn hàng.
     */
    public String renderOrderConfirmation(
            String customerName,
            String orderId,
            String status,
            BigDecimal totalAmount,
            List<Map<String, Object>> items
    ) {
        return orderConfirmation
                .data("customerName", customerName)
                .data("orderId", orderId)
                .data("status", status)
                .data("totalAmount", totalAmount)
                .data("items", items == null ? List.of() : items)
                .render();
    }

    /**
     * Render email cập nhật trạng thái đơn hàng.
     */
    public String renderOrderStatus(
            String customerName,
            String orderId,
            String status,
            String statusDescription
    ) {
        return orderStatus
                .data("customerName", customerName)
                .data("orderId", orderId)
                .data("status", status)
                .data("statusDescription", statusDescription)
                .render();
    }

    /**
     * Render email xác nhận thanh toán.
     */
    public String renderPaymentCompleted(
            String customerName,
            String orderId,
            BigDecimal amount,
            String transactionId,
            String paymentMethod
    ) {
        return paymentCompleted
                .data("customerName", customerName)
                .data("orderId", orderId)
                .data("amount", amount)
                .data("transactionId", transactionId)
                .data("paymentMethod", paymentMethod)
                .render();
    }

    /**
     * Render email khôi phục mật khẩu.
     */
    public String renderPasswordReset(
            String customerName,
            String resetUrl,
            int expiresMinutes
    ) {
        return passwordReset
                .data("customerName", customerName)
                .data("resetUrl", resetUrl)
                .data("expiresMinutes", expiresMinutes)
                .render();
    }

    /**
     * Render email thông báo kết quả duyệt đơn thuốc.
     */
    public String renderPrescriptionReviewed(
            String customerName,
            String prescriptionId,
            String decision,
            String pharmacistName,
            String reason
    ) {
        return prescriptionReviewed
                .data("customerName", customerName)
                .data("prescriptionId", prescriptionId)
                .data("decision", decision)
                .data("pharmacistName", pharmacistName)
                .data("reason", reason)
                .render();
    }

    /**
     * Render email cảnh báo tồn kho thấp.
     */
    public String renderInventoryLowStock(
            String medicineName,
            int availableQuantity,
            int threshold
    ) {
        return inventoryLowStock
                .data("medicineName", medicineName)
                .data("availableQuantity", availableQuantity)
                .data("threshold", threshold)
                .render();
    }

    /**
     * Render email cảnh báo thuốc sắp hết hạn.
     */
    public String renderInventoryNearExpiry(
            String medicineName,
            String batchNumber,
            String expiryDate,
            int quantity,
            int daysLeft
    ) {
        return inventoryNearExpiry
                .data("medicineName", medicineName)
                .data("batchNumber", batchNumber)
                .data("expiryDate", expiryDate)
                .data("quantity", quantity)
                .data("daysLeft", daysLeft)
                .render();
    }
}
