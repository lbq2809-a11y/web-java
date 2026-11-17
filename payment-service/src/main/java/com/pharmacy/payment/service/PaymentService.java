package com.pharmacy.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.payment.client.OrderServiceClient;
import com.pharmacy.payment.dto.*;
import com.pharmacy.payment.entity.*;
import com.pharmacy.payment.exception.GlobalExceptionMapper;
import com.pharmacy.payment.repository.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PaymentService {

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    PaymentTransactionRepository transactionRepository;

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    @RestClient
    OrderServiceClient orderServiceClient;

    @Inject
    VnPayService vnPayService;

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public PaymentResponse createPayment(
        CreatePaymentRequest request,
        String authorization,
        String correlationId,
        String clientIp
    ) {
        validateAuthorization(authorization);

        String traceId = normalizeCorrelationId(correlationId);

        OrderSnapshot order = orderServiceClient.getOrder(
            request.orderId(),
            authorization,
            traceId
        );

        if (order == null || order.id() == null
                || !order.id().equals(request.orderId())) {
            throw error(502, "Order Service trả về dữ liệu đơn hàng không hợp lệ");
        }

        if (order.totalAmount() == null
                || order.totalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw error(400, "Tổng tiền đơn hàng không hợp lệ");
        }

        if ("DA_HUY".equalsIgnoreCase(order.status())) {
            throw error(409, "Không thể thanh toán đơn hàng đã bị hủy");
        }

        if (request.method() == PaymentMethod.MOMO) {
            throw error(501, "Phương thức MOMO chưa được tích hợp");
        }

        Payment payment = paymentRepository
            .findByOrderId(request.orderId())
            .orElse(null);

        if (payment != null) {
            if (payment.status == PaymentStatus.COMPLETED) {
                return toResponse(payment, clientIp);
            }

            if (payment.status == PaymentStatus.PENDING) {
                if (payment.method != request.method()) {
                    throw error(409,
                        "Đơn hàng đang có giao dịch chờ thanh toán bằng phương thức khác");
                }
                return toResponse(payment, clientIp);
            }

            if (payment.status == PaymentStatus.CANCELLED) {
                throw error(409, "Giao dịch đã bị hủy");
            }

            payment.method = request.method();
            payment.status = PaymentStatus.PENDING;
            payment.externalTransactionId = null;
        } else {
            payment = new Payment();
            payment.id = UUID.randomUUID();
            payment.orderId = order.id();
            payment.customerId = order.customerId();
            payment.customerEmail = order.customerEmail();
            payment.amount = order.totalAmount();
            payment.method = request.method();
            payment.status = PaymentStatus.PENDING;

            paymentRepository.persist(payment);
        }

        recordTransaction(payment, "PAYMENT_INITIATED", "PENDING",
            "Payment method: " + request.method());

        return toResponse(payment, clientIp);
    }

    public PaymentResponse getPayment(
        UUID paymentId,
        String authorization,
        String correlationId
    ) {
        validateAuthorization(authorization);

        Payment payment = paymentRepository.findById(paymentId);
        if (payment == null) {
            throw error(404, "Không tìm thấy giao dịch thanh toán");
        }

        orderServiceClient.getOrder(
            payment.orderId,
            authorization,
            normalizeCorrelationId(correlationId)
        );

        return toResponse(payment, null);
    }

    public PaymentResponse getPaymentByOrderId(
        UUID orderId,
        String authorization,
        String correlationId
    ) {
        validateAuthorization(authorization);

        Payment payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> error(404, "Chưa có giao dịch thanh toán cho đơn hàng này"));

        orderServiceClient.getOrder(
            payment.orderId,
            authorization,
            normalizeCorrelationId(correlationId)
        );

        return toResponse(payment, null);
    }

    @Transactional
    public PaymentResponse confirmCod(
        UUID paymentId,
        String correlationId
    ) {
        return confirmManualPayment(
            paymentId,
            correlationId,
            PaymentMethod.COD,
            "Giao dịch này không sử dụng phương thức COD",
            "COD_CASH_COLLECTED",
            "Nhân viên xác nhận đã thu tiền COD"
        );
    }

    @Transactional
    public PaymentResponse confirmQr(
        UUID paymentId,
        String correlationId
    ) {
        return confirmManualPayment(
            paymentId,
            correlationId,
            PaymentMethod.QR,
            "Giao dịch này không sử dụng phương thức QR",
            "QR_TRANSFER_CONFIRMED",
            "Nhân viên xác nhận đã nhận tiền chuyển khoản QR"
        );
    }

    // Xử lý chung cho xác nhận thủ công (COD / QR) và phát sự kiện Outbox payment.completed
    private PaymentResponse confirmManualPayment(
        UUID paymentId,
        String correlationId,
        PaymentMethod expectedMethod,
        String wrongMethodMessage,
        String transactionType,
        String transactionNote
    ) {
        Payment payment = paymentRepository.findById(paymentId);

        if (payment == null) {
            throw error(404, "Không tìm thấy giao dịch thanh toán");
        }

        if (payment.method != expectedMethod) {
            throw error(409, wrongMethodMessage);
        }

        if (payment.status == PaymentStatus.COMPLETED) {
            return toResponse(payment, null);
        }

        if (payment.status != PaymentStatus.PENDING) {
            throw error(409, "Giao dịch không ở trạng thái chờ xác nhận");
        }

        payment.status = PaymentStatus.COMPLETED;

        recordTransaction(
            payment,
            transactionType,
            "COMPLETED",
            transactionNote
        );

        createCompletedOutboxEvent(
            payment,
            normalizeCorrelationId(correlationId)
        );

        return toResponse(payment, null);
    }

    private PaymentResponse toResponse(Payment payment, String clientIp) {
        String redirectUrl = null;
        String qrContent = null;

        if (payment.status == PaymentStatus.PENDING) {
            if (payment.method == PaymentMethod.VNPAY) {
                redirectUrl = vnPayService.createPaymentUrl(
                    payment.id.toString().replace("-", ""),
                    payment.amount,
                    clientIp
                );
            } else if (payment.method == PaymentMethod.QR) {
                qrContent = "PHARMACY-PAYMENT|"
                    + payment.id + "|" + payment.amount.toPlainString();
            }
        }

        return new PaymentResponse(
            payment.id,
            payment.orderId,
            payment.amount,
            payment.method,
            payment.status,
            payment.externalTransactionId,
            redirectUrl,
            qrContent,
            payment.createdAt
        );
    }

    private void recordTransaction(
        Payment payment,
        String type,
        String status,
        String payload
    ) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.id = UUID.randomUUID();
        transaction.paymentId = payment.id;
        transaction.transactionType = type;
        transaction.status = status;
        transaction.rawPayload = payload;

        transactionRepository.persist(transaction);
    }

    private void createCompletedOutboxEvent(
        Payment payment,
        String correlationId
    ) {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
            UUID.randomUUID(),
            "payment.completed",
            Instant.now(),
            correlationId,
            payment.id,
            payment.orderId,
            payment.customerId,
            payment.customerEmail,
            payment.amount,
            payment.method.name(),
            payment.externalTransactionId
        );

        try {
            OutboxEvent outbox = new OutboxEvent();
            outbox.id = event.eventId();
            outbox.aggregateId = payment.id;
            outbox.eventType = event.eventType();
            outbox.topic = "payment.completed";
            outbox.payload = objectMapper.writeValueAsString(event);
            outbox.attempts = 0;

            outboxRepository.persist(outbox);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "Không thể tạo Outbox event thanh toán", e
            );
        }
    }

    private void validateAuthorization(String authorization) {
        if (authorization == null
                || !authorization.startsWith("Bearer ")) {
            throw error(401, "Thiếu Authorization Bearer Token");
        }
    }

    private String normalizeCorrelationId(String correlationId) {
        return correlationId == null || correlationId.isBlank()
            ? UUID.randomUUID().toString()
            : correlationId;
    }

    private WebApplicationException error(int status, String message) {
        return new WebApplicationException(
            Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(
                    GlobalExceptionMapper.codeForStatus(status),
                    message,
                    List.of()
                ))
                .build()
        );
    }
}