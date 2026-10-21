package com.pharmacy.order.service;

import com.pharmacy.order.client.MedicineServiceClient;
import com.pharmacy.order.client.PrescriptionServiceClient;
import com.pharmacy.order.dto.CancelOrderRequest;
import com.pharmacy.order.dto.CheckoutRequest;
import com.pharmacy.order.dto.LinkOrderRequest;
import com.pharmacy.order.dto.MedicineResponse;
import com.pharmacy.order.dto.OrderCreatedEvent;
import com.pharmacy.order.dto.OrderPageResponse;
import com.pharmacy.order.dto.OrderResponse;
import com.pharmacy.order.dto.OrderStatusChangedEvent;
import com.pharmacy.order.dto.OrderStatusHistoryResponse;
import com.pharmacy.order.dto.PaymentCompletedEvent;
import com.pharmacy.order.dto.PaymentFailedEvent;
import com.pharmacy.order.dto.RefundOrderRequest;
import com.pharmacy.order.dto.UpdateOrderStatusRequest;
import com.pharmacy.order.dto.ValidateItemRequest;
import com.pharmacy.order.dto.ValidatePrescriptionRequest;
import com.pharmacy.order.dto.ValidatePrescriptionResponse;
import com.pharmacy.order.entity.Order;
import com.pharmacy.order.entity.OrderItem;
import com.pharmacy.order.entity.OrderStatus;
import com.pharmacy.order.entity.OrderStatusHistory;
import com.pharmacy.order.entity.ProcessedEvent;
import com.pharmacy.order.entity.ShippingAddress;
import com.pharmacy.order.exception.ApiException;
import com.pharmacy.order.mapper.OrderMapper;
import com.pharmacy.order.repository.OrderRepository;
import com.pharmacy.order.repository.OrderStatusHistoryRepository;
import com.pharmacy.order.repository.ProcessedEventRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service nghiệp vụ xử lý đặt hàng (Checkout), tra cứu đơn hàng và điều phối liên service
 */
@ApplicationScoped
public class OrderService {

    private static final Logger LOG = Logger.getLogger(OrderService.class);

    @Inject
    OrderRepository orderRepo;

    @Inject
    OrderStatusHistoryRepository statusHistoryRepo;

    @Inject
    ProcessedEventRepository processedEventRepo;

    @Inject
    CartRedisService cartRedisService;

    @Inject
    @RestClient
    MedicineServiceClient medicineClient;

    @Inject
    @RestClient
    PrescriptionServiceClient prescriptionClient;

    @Inject
    OrderKafkaProducer kafkaProducer;

    @Inject
    OrderMapper mapper;

    @Inject
    JsonWebToken jwt;

    @Inject
    SecurityIdentity securityIdentity;

    /**
     * Nghiệp vụ Checkout:
     * 1. Kiểm tra Idempotency-Key chống trùng lặp.
     * 2. Đọc giỏ hàng từ Redis.
     * 3. Lấy giá niêm yết thời gian thực từ medicine-service.
     * 4. Kiểm tra điều kiện thuốc kê đơn (ETC) với prescription-service.
     * 5. Lưu Order, OrderItem (snapshot), ShippingAddress, OrderStatusHistory trong 1 transaction.
     * 6. Khóa đơn thuốc (linkOrder).
     * 7. Xóa sạch giỏ hàng trên Redis.
     * 8. Phát event Kafka order.created.
     */
    @Transactional
    public OrderResponse checkout(String idempotencyKey, CheckoutRequest req) {
        UUID customerId = getUserIdFromJwt();
        String customerName = jwt.getClaim("name");
        String customerEmail = jwt.getClaim("email");

        // 1. Kiểm tra Idempotency-Key
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Order> existingOrder = orderRepo.findByIdempotencyKey(idempotencyKey.trim());
            if (existingOrder.isPresent()) {
                LOG.infof("Yêu cầu trùng lặp với Idempotency-Key: %s. Trả lại đơn %s đã tạo trước đó.",
                        idempotencyKey, existingOrder.get().getId());
                return mapper.toOrderResponse(existingOrder.get());
            }
        }

        // 2. Lấy giỏ hàng từ Redis
        Map<String, Integer> cartMap = cartRedisService.getCart(customerId);
        if (cartMap == null || cartMap.isEmpty()) {
            throw ApiException.badRequest("CART_EMPTY", "Giỏ hàng của bạn đang trống, vui lòng thêm sản phẩm trước khi đặt hàng");
        }

        // 3. Lấy giá và thông tin thuốc mới nhất từ medicine-service (Batch)
        String ids = String.join(",", cartMap.keySet());
        List<MedicineResponse> medicines;
        try {
            medicines = medicineClient.getMedicinesBatch(ids);
        } catch (Exception e) {
            LOG.errorf("Không thể kết nối sang medicine-service để xác thực giỏ hàng: %s", e.getMessage());
            throw new ApiException("MEDICINE_SERVICE_UNAVAILABLE",
                    "Dịch vụ tra cứu thuốc tạm thời không khả dụng, vui lòng thử lại sau: " + e.getMessage(),
                    Response.Status.SERVICE_UNAVAILABLE);
        }

        if (medicines == null || medicines.isEmpty()) {
            throw ApiException.badRequest("MEDICINE_NOT_FOUND", "Không tìm thấy thông tin các thuốc trong giỏ hàng");
        }

        Map<String, MedicineResponse> medMap = medicines.stream()
                .collect(Collectors.toMap(MedicineResponse::id, m -> m));

        // Kiểm tra xem có thuốc nào ngừng kinh doanh hoặc không còn tồn tại không
        List<String> inactiveMedicines = new ArrayList<>();
        for (String medId : cartMap.keySet()) {
            MedicineResponse med = medMap.get(medId);
            if (med == null || !med.active()) {
                inactiveMedicines.add(med != null ? med.name() : medId);
            }
        }
        if (!inactiveMedicines.isEmpty()) {
            throw ApiException.badRequest(
                    "MEDICINE_INACTIVE_OR_NOT_FOUND",
                    "Một số sản phẩm trong giỏ hàng hiện đã ngừng kinh doanh hoặc không tồn tại",
                    inactiveMedicines
            );
        }

        // 4. Kiểm tra phân loại thuốc kê đơn ETC
        List<MedicineResponse> etcMedicines = medicines.stream()
                .filter(m -> "ETC".equalsIgnoreCase(m.prescriptionType()))
                .toList();

        if (!etcMedicines.isEmpty()) {
            if (req.prescriptionId() == null) {
                List<String> etcNames = etcMedicines.stream().map(MedicineResponse::name).toList();
                throw ApiException.badRequest(
                        "PRESCRIPTION_REQUIRED",
                        "Đơn hàng có chứa thuốc kê đơn (ETC), bắt buộc phải có mã đơn thuốc (prescriptionId) đã được duyệt",
                        etcNames
                );
            }

            // Gọi prescription-service kiểm tra tính hợp lệ của đơn thuốc cho các thuốc ETC
            List<ValidateItemRequest> validateItems = etcMedicines.stream()
                    .map(m -> new ValidateItemRequest(m.id(), cartMap.get(m.id())))
                    .toList();

            ValidatePrescriptionRequest validateReq = new ValidatePrescriptionRequest(customerId, validateItems);
            try {
                ValidatePrescriptionResponse valRes = prescriptionClient.validateForOrder(req.prescriptionId(), validateReq);
                if (!valRes.valid()) {
                    throw ApiException.unprocessable("PRESCRIPTION_INVALID", "Đơn thuốc không hợp lệ để mua danh mục này", valRes.violations());
                }
            } catch (ApiException e) {
                throw e;
            } catch (WebApplicationException e) {
                int status = e.getResponse() != null ? e.getResponse().getStatus() : 400;
                String msg = e.getMessage() != null ? e.getMessage() : "Đơn thuốc không hợp lệ hoặc đã hết hạn";
                throw new ApiException("PRESCRIPTION_INVALID", msg, status);
            } catch (Exception e) {
                LOG.errorf("Lỗi khi kết nối prescription-service: %s", e.getMessage());
                throw new ApiException("PRESCRIPTION_SERVICE_UNAVAILABLE",
                        "Dịch vụ thẩm định đơn thuốc tạm thời không khả dụng: " + e.getMessage(),
                        Response.Status.SERVICE_UNAVAILABLE);
            }
        }

        // 5. Tạo đơn hàng và tính tổng tiền
        Order order = new Order();
        order.setCustomerId(customerId);
        order.setCustomerName(customerName);
        order.setCustomerEmail(customerEmail);
        order.setPrescriptionId(req.prescriptionId());
        order.setStatus(OrderStatus.CHO_XU_LY);
        order.setIdempotencyKey(idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey.trim() : null);
        order.setNotes(req.notes() != null ? req.notes().trim() : null);

        BigDecimal totalAmount = BigDecimal.ZERO;

        // Lưu SNAPSHOT từng món hàng
        for (Map.Entry<String, Integer> entry : cartMap.entrySet()) {
            MedicineResponse med = medMap.get(entry.getKey());
            int qty = entry.getValue();
            BigDecimal unitPrice = med.price() != null ? med.price() : BigDecimal.ZERO;
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(qty));
            totalAmount = totalAmount.add(subtotal);

            OrderItem item = new OrderItem();
            item.setMedicineId(med.id());
            item.setMedicineCode(med.code());
            item.setMedicineName(med.name());
            item.setUnit(med.unit());
            item.setPrescriptionType(med.prescriptionType());
            item.setUnitPrice(unitPrice);
            item.setQuantity(qty);
            item.setSubtotal(subtotal);

            order.addItem(item);
        }

        order.setTotalAmount(totalAmount);

        // Lưu SNAPSHOT địa chỉ giao hàng
        ShippingAddress address = new ShippingAddress();
        address.setRecipientName(req.shippingAddress().recipientName().trim());
        address.setPhone(req.shippingAddress().phone().trim());
        address.setAddressLine(req.shippingAddress().addressLine().trim());
        order.setShippingAddress(address);

        // Ghi nhận bản ghi lịch sử trạng thái đầu tiên
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOldStatus(null);
        history.setNewStatus(OrderStatus.CHO_XU_LY.name());
        history.setChangedBy(customerId);
        history.setReason("Khách hàng tạo đơn hàng mới");
        order.addStatusHistory(history);

        orderRepo.persist(order);
        LOG.infof("Đã lưu thành công đơn hàng %s (Khách: %s, Tổng tiền: %s)",
                order.getId(), customerId, totalAmount);

        // 6. Gắn đơn thuốc vào đơn hàng nếu có
        if (req.prescriptionId() != null) {
            try {
                prescriptionClient.linkOrder(req.prescriptionId(), new LinkOrderRequest(order.getId(), customerId));
                LOG.infof("Đã gắn đơn thuốc %s vào đơn hàng %s", req.prescriptionId(), order.getId());
            } catch (Exception e) {
                LOG.warnf("Không thể liên kết đơn thuốc %s với order %s: %s",
                        req.prescriptionId(), order.getId(), e.getMessage());
            }
        }

        // 7. Xóa sạch giỏ hàng trên Redis
        cartRedisService.clearCart(customerId);

        // 8. Phát event Kafka order.created
        List<OrderCreatedEvent.OrderItemEvent> eventItems = order.getItems().stream()
                .map(it -> new OrderCreatedEvent.OrderItemEvent(
                        it.getMedicineId(),
                        it.getMedicineName(),
                        it.getPrescriptionType(),
                        it.getUnitPrice(),
                        it.getQuantity()
                ))
                .toList();

        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(),
                "order.created",
                Instant.now(),
                UUID.randomUUID().toString(),
                order.getId(),
                customerId,
                customerEmail,
                customerName,
                req.prescriptionId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                eventItems
        );
        kafkaProducer.sendOrderCreatedEvent(event);

        return mapper.toOrderResponse(order);
    }

    /**
     * Khách hàng xem danh sách đơn hàng của chính mình (phân trang)
     */
    public OrderPageResponse getMyOrders(OrderStatus status, int page, int size) {
        UUID customerId = getUserIdFromJwt();
        int safePage = Math.max(page, 0);
        int safeSize = (size > 0 && size <= 100) ? size : 20;

        List<Order> list = orderRepo.findByCustomerId(customerId, status, safePage, safeSize);
        long total = orderRepo.countByCustomerId(customerId, status);
        int totalPages = (int) Math.ceil((double) total / safeSize);

        List<OrderResponse> responses = list.stream().map(mapper::toOrderResponse).toList();
        return new OrderPageResponse(responses, safePage, safeSize, total, totalPages);
    }

    /**
     * Xem thông tin chi tiết một đơn hàng:
     * - DUOC_SI và ADMIN xem được mọi đơn hàng
     * - KHACH_HANG chỉ xem được đơn hàng của chính mình
     */
    public OrderResponse getOrderById(UUID id) {
        Order order = orderRepo.findByIdWithDetails(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng với mã: " + id));

        boolean isPharmacistOrAdmin = securityIdentity.hasRole("DUOC_SI") || securityIdentity.hasRole("ADMIN");
        if (!isPharmacistOrAdmin) {
            UUID currentUserId = getUserIdFromJwt();
            if (!order.getCustomerId().equals(currentUserId)) {
                throw ApiException.forbidden("Bạn không có quyền xem đơn hàng của người khác");
            }
        }

        return mapper.toOrderResponse(order);
    }

    /**
     * Dược sĩ hoặc Admin cập nhật trạng thái đơn hàng theo máy trạng thái hợp lệ
     */
    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, UpdateOrderStatusRequest req) {
        Order order = orderRepo.findByIdWithDetails(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng với mã: " + orderId));

        validateTransition(order.getStatus(), req.newStatus());

        UUID changedBy = getUserIdFromJwt();
        applyStatusChange(order, req.newStatus(), changedBy, req.reason().trim());

        LOG.infof("Đã cập nhật trạng thái đơn hàng %s sang %s bởi %s",
                order.getId(), req.newStatus(), changedBy);

        return mapper.toOrderResponse(order);
    }

    /**
     * Khách hàng hủy đơn hàng của chính mình (chỉ cho phép khi đang ở trạng thái CHO_XU_LY)
     */
    @Transactional
    public OrderResponse cancelOrder(UUID orderId, CancelOrderRequest req) {
        Order order = orderRepo.findByIdWithDetails(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng với mã: " + orderId));

        boolean isPharmacistOrAdmin = securityIdentity.hasRole("DUOC_SI") || securityIdentity.hasRole("ADMIN");
        UUID currentUserId = getUserIdFromJwt();
        if (!isPharmacistOrAdmin && !order.getCustomerId().equals(currentUserId)) {
            throw ApiException.forbidden("Bạn không có quyền hủy đơn hàng của người khác");
        }

        if (order.getStatus() != OrderStatus.CHO_XU_LY) {
            throw ApiException.badRequest("INVALID_STATUS_TRANSITION",
                    String.format("Chỉ có thể hủy đơn hàng khi đang ở trạng thái CHO_XU_LY (trạng thái hiện tại: %s)", order.getStatus()));
        }

        applyStatusChange(order, OrderStatus.DA_HUY, currentUserId, req.reason().trim());

        LOG.infof("Đơn hàng %s đã bị hủy bởi %s với lý do: %s",
                order.getId(), currentUserId, req.reason());

        return mapper.toOrderResponse(order);
    }

    /**
     * Khách hàng yêu cầu hoàn trả đơn hàng (chỉ cho phép khi đang ở trạng thái HOAN_THANH)
     */
    @Transactional
    public OrderResponse refundOrder(UUID orderId, RefundOrderRequest req) {
        Order order = orderRepo.findByIdWithDetails(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng với mã: " + orderId));

        boolean isPharmacistOrAdmin = securityIdentity.hasRole("DUOC_SI") || securityIdentity.hasRole("ADMIN");
        UUID currentUserId = getUserIdFromJwt();
        if (!isPharmacistOrAdmin && !order.getCustomerId().equals(currentUserId)) {
            throw ApiException.forbidden("Bạn không có quyền yêu cầu hoàn trả đơn hàng của người khác");
        }

        if (order.getStatus() != OrderStatus.HOAN_THANH) {
            throw ApiException.badRequest("INVALID_STATUS_TRANSITION",
                    String.format("Chỉ có thể yêu cầu hoàn trả khi đơn hàng đã ở trạng thái HOAN_THANH (trạng thái hiện tại: %s)", order.getStatus()));
        }

        applyStatusChange(order, OrderStatus.HOAN_TRA, currentUserId, req.reason().trim());

        LOG.infof("Đơn hàng %s đã được yêu cầu hoàn trả bởi %s với lý do: %s",
                order.getId(), currentUserId, req.reason());

        return mapper.toOrderResponse(order);
    }

    /**
     * Xem lịch sử thay đổi trạng thái của đơn hàng (Audit Trail)
     */
    public List<OrderStatusHistoryResponse> getOrderHistory(UUID orderId) {
        Order order = orderRepo.findByIdOptional(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng với mã: " + orderId));

        boolean isPharmacistOrAdmin = securityIdentity.hasRole("DUOC_SI") || securityIdentity.hasRole("ADMIN");
        if (!isPharmacistOrAdmin) {
            UUID currentUserId = getUserIdFromJwt();
            if (!order.getCustomerId().equals(currentUserId)) {
                throw ApiException.forbidden("Bạn không có quyền xem lịch sử đơn hàng của người khác");
            }
        }

        List<OrderStatusHistory> historyList = statusHistoryRepo.findByOrderId(orderId);
        return historyList.stream()
                .map(mapper::toOrderStatusHistoryResponse)
                .toList();
    }

    /**
     * Xử lý sự kiện payment.completed từ payment-service (Idempotent)
     */
    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        if (event == null || event.eventId() == null || event.orderId() == null) {
            LOG.warn("Bỏ qua sự kiện payment.completed không hợp lệ");
            return;
        }

        if (processedEventRepo.isEventProcessed(event.eventId())) {
            LOG.infof("Sự kiện payment.completed [%s] đã được xử lý trước đó, bỏ qua", event.eventId());
            return;
        }

        LOG.infof("Nhận sự kiện payment.completed cho đơn hàng [%s], paymentId [%s]",
                event.orderId(), event.paymentId());

        Optional<Order> orderOpt = orderRepo.findByIdOptional(event.orderId());
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            OrderStatusHistory history = new OrderStatusHistory();
            history.setOldStatus(order.getStatus().name());
            history.setNewStatus(order.getStatus().name());
            history.setChangedBy(null);
            history.setReason(String.format("Thanh toán thành công qua %s (Mã GD: %s, Số tiền: %s)",
                    event.paymentMethod(),
                    event.externalTransactionId() != null ? event.externalTransactionId() : "N/A",
                    event.amount()));
            order.addStatusHistory(history);
            order.setUpdatedAt(Instant.now());
            orderRepo.persist(order);
        } else {
            LOG.warnf("Không tìm thấy đơn hàng [%s] cho sự kiện payment.completed", event.orderId());
        }

        processedEventRepo.persist(new ProcessedEvent(event.eventId(), "payment.completed"));
    }

    /**
     * Xử lý sự kiện payment.failed từ payment-service (Idempotent)
     */
    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        if (event == null || event.eventId() == null || event.orderId() == null) {
            LOG.warn("Bỏ qua sự kiện payment.failed không hợp lệ");
            return;
        }

        if (processedEventRepo.isEventProcessed(event.eventId())) {
            LOG.infof("Sự kiện payment.failed [%s] đã được xử lý trước đó, bỏ qua", event.eventId());
            return;
        }

        LOG.infof("Nhận sự kiện payment.failed cho đơn hàng [%s], lý do: %s",
                event.orderId(), event.reason());

        Optional<Order> orderOpt = orderRepo.findByIdOptional(event.orderId());
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            if (order.getStatus() == OrderStatus.CHO_XU_LY) {
                String reason = "Hủy đơn do thanh toán thất bại (" +
                        (event.paymentMethod() != null ? event.paymentMethod() + ": " : "") +
                        (event.reason() != null ? event.reason() : "Không rõ lý do") + ")";
                applyStatusChange(order, OrderStatus.DA_HUY, null, reason);
                LOG.infof("Đã tự động hủy đơn [%s] do thanh toán thất bại", order.getId());
            } else {
                LOG.infof("Đơn hàng [%s] đang ở trạng thái %s, không tự động hủy", order.getId(), order.getStatus());
            }
        } else {
            LOG.warnf("Không tìm thấy đơn hàng [%s] cho sự kiện payment.failed", event.orderId());
        }

        processedEventRepo.persist(new ProcessedEvent(event.eventId(), "payment.failed"));
    }

    /**
     * Kiểm tra tính hợp lệ của luồng chuyển trạng thái đơn hàng
     */
    private void validateTransition(OrderStatus current, OrderStatus next) {
        if (current == null || next == null) {
            throw ApiException.badRequest("INVALID_STATUS_TRANSITION", "Trạng thái không được để trống");
        }
        if (current == next) {
            throw ApiException.badRequest("INVALID_STATUS_TRANSITION",
                    String.format("Đơn hàng hiện tại đã ở trạng thái %s", current));
        }

        boolean valid = false;
        switch (current) {
            case CHO_XU_LY -> valid = (next == OrderStatus.DANG_GIAO || next == OrderStatus.DA_HUY);
            case DANG_GIAO -> valid = (next == OrderStatus.HOAN_THANH);
            case HOAN_THANH -> valid = (next == OrderStatus.HOAN_TRA);
            case DA_HUY, HOAN_TRA -> valid = false;
        }

        if (!valid) {
            throw ApiException.badRequest("INVALID_STATUS_TRANSITION",
                    String.format("Không thể chuyển trạng thái đơn hàng từ %s sang %s", current, next));
        }
    }

    /**
     * Cập nhật trạng thái, ghi audit log và phát Kafka event order.status-changed
     */
    private void applyStatusChange(Order order, OrderStatus newStatus, UUID changedBy, String reason) {
        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        order.setUpdatedAt(Instant.now());

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOldStatus(oldStatus != null ? oldStatus.name() : null);
        history.setNewStatus(newStatus.name());
        history.setChangedBy(changedBy);
        history.setReason(reason);
        order.addStatusHistory(history);

        orderRepo.persist(order);

        OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                UUID.randomUUID(),
                "order.status-changed",
                Instant.now(),
                UUID.randomUUID().toString(),
                order.getId(),
                order.getCustomerId(),
                order.getCustomerEmail(),
                order.getCustomerName(),
                oldStatus != null ? oldStatus.name() : null,
                newStatus.name(),
                reason
        );
        kafkaProducer.sendOrderStatusChangedEvent(event);
    }

    private UUID getUserIdFromJwt() {
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw ApiException.badRequest("UNAUTHENTICATED", "Không xác định được danh tính người dùng từ token");
        }
        try {
            return UUID.fromString(sub.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_USER_ID", "ID người dùng trong token không đúng định dạng UUID");
        }
    }
}

