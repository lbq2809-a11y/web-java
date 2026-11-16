package com.pharmacy.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.payment.dto.*;
import com.pharmacy.payment.entity.*;
import com.pharmacy.payment.repository.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class VnPayCallbackService {

    @Inject
    VnPayService vnPayService;

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    PaymentTransactionRepository transactionRepository;

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public VnPayIpnResponse processIpn(Map<String, String> params) {

        // 1. Xác minh chữ ký trước khi xử lý dữ liệu.
        if (!vnPayService.verifySignature(params)) {
            return response("97", "Invalid signature");
        }

        if (!vnPayService.isMerchantCode(params.get("vnp_TmnCode"))) {
            return response("97", "Invalid merchant code");
        }

        // 2. Tìm Payment theo mã tham chiếu VNPay.
        UUID paymentId;

        try {
            paymentId = parsePaymentId(params.get("vnp_TxnRef"));
        } catch (Exception e) {
            return response("01", "Order not found");
        }

        Payment payment = paymentRepository.findById(paymentId);

        if (payment == null || payment.method != PaymentMethod.VNPAY) {
            return response("01", "Order not found");
        }

        // 3. Đối chiếu số tiền, VNPay gửi số tiền nhân 100.
        try {
            long receivedAmount = Long.parseLong(params.get("vnp_Amount"));

            long expectedAmount = payment.amount
                .multiply(BigDecimal.valueOf(100))
                .longValueExact();

            if (receivedAmount != expectedAmount) {
                return response("04", "Invalid amount");
            }

        } catch (Exception e) {
            return response("04", "Invalid amount");
        }

        // 4. Chống xử lý callback trùng.
        if (payment.status != PaymentStatus.PENDING) {
            return response("02", "Order already confirmed");
        }

        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");

        if (responseCode == null || transactionStatus == null) {
            return response("99", "Missing transaction status");
        }

        boolean successful =
            "00".equals(responseCode)
            && "00".equals(transactionStatus);

        String transactionNo = params.get("vnp_TransactionNo");

        payment.externalTransactionId = transactionNo;
        payment.status = successful
            ? PaymentStatus.COMPLETED
            : PaymentStatus.FAILED;

        // 5. Ghi nhật ký giao dịch.
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.id = UUID.randomUUID();
        transaction.paymentId = payment.id;
        transaction.transactionType = "VNPAY_IPN";
        transaction.externalTransactionId = transactionNo;
        transaction.status = payment.status.name();

        try {
            transaction.rawPayload = objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể lưu IPN payload", e);
        }

        transactionRepository.persist(transaction);

        // 6. Ghi Outbox event trong cùng transaction.
        if (successful) {
            createCompletedEvent(payment);
        } else {
            createFailedEvent(payment, responseCode, transactionStatus);
        }

        return response("00", "Confirm Success");
    }

    private UUID parsePaymentId(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Missing transaction reference");
        }

        // Payment Service gửi UUID dạng 32 ký tự không có dấu gạch ngang.
        if (reference.matches("(?i)[0-9a-f]{32}")) {
            String formatted = reference.substring(0, 8) + "-"
                + reference.substring(8, 12) + "-"
                + reference.substring(12, 16) + "-"
                + reference.substring(16, 20) + "-"
                + reference.substring(20);

            return UUID.fromString(formatted);
        }

        return UUID.fromString(reference);
    }

    private void createCompletedEvent(Payment payment) {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
            UUID.randomUUID(),
            "payment.completed",
            Instant.now(),
            UUID.randomUUID().toString(),
            payment.id,
            payment.orderId,
            payment.customerId,
            payment.customerEmail,
            payment.amount,
            payment.method.name(),
            payment.externalTransactionId
        );

        saveOutbox(
            payment.id,
            event.eventId(),
            event.eventType(),
            "payment.completed",
            event
        );
    }

    private void createFailedEvent(
        Payment payment,
        String responseCode,
        String transactionStatus
    ) {
        PaymentFailedEvent event = new PaymentFailedEvent(
            UUID.randomUUID(),
            "payment.failed",
            Instant.now(),
            UUID.randomUUID().toString(),
            payment.id,
            payment.orderId,
            payment.customerId,
            payment.customerEmail,
            payment.amount,
            payment.method.name(),
            payment.externalTransactionId,
            responseCode,
            "VNPay transaction status: " + transactionStatus
        );

        saveOutbox(
            payment.id,
            event.eventId(),
            event.eventType(),
            "payment.failed",
            event
        );
    }

    private void saveOutbox(
        UUID paymentId,
        UUID eventId,
        String eventType,
        String topic,
        Object event
    ) {
        try {
            OutboxEvent outbox = new OutboxEvent();
            outbox.id = eventId;
            outbox.aggregateId = paymentId;
            outbox.eventType = eventType;
            outbox.topic = topic;
            outbox.payload = objectMapper.writeValueAsString(event);
            outbox.attempts = 0;

            outboxRepository.persist(outbox);

        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "Không thể tạo Outbox event", e
            );
        }
    }

    private VnPayIpnResponse response(String code, String message) {
        return new VnPayIpnResponse(code, message);
    }
}