package com.pharmacy.prescription.service;

import com.pharmacy.prescription.dto.ApproveItemRequest;
import com.pharmacy.prescription.dto.ApprovePrescriptionRequest;
import com.pharmacy.prescription.dto.LinkOrderRequest;
import com.pharmacy.prescription.dto.LinkOrderResponse;
import com.pharmacy.prescription.dto.PrescriptionPageResponse;
import com.pharmacy.prescription.dto.PrescriptionResponse;
import com.pharmacy.prescription.dto.PrescriptionReviewedEvent;
import com.pharmacy.prescription.dto.PrescriptionUploadForm;
import com.pharmacy.prescription.dto.RejectPrescriptionRequest;
import com.pharmacy.prescription.dto.ValidateItemRequest;
import com.pharmacy.prescription.dto.ValidatePrescriptionRequest;
import com.pharmacy.prescription.dto.ValidatePrescriptionResponse;
import com.pharmacy.prescription.entity.Prescription;
import com.pharmacy.prescription.entity.PrescriptionItem;
import com.pharmacy.prescription.entity.PrescriptionOrderLink;
import com.pharmacy.prescription.entity.PrescriptionStatus;
import com.pharmacy.prescription.exception.ApiException;
import com.pharmacy.prescription.mapper.PrescriptionMapper;
import com.pharmacy.prescription.repository.PrescriptionItemRepository;
import com.pharmacy.prescription.repository.PrescriptionOrderLinkRepository;
import com.pharmacy.prescription.repository.PrescriptionRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service xử lý toàn bộ nghiệp vụ đơn thuốc: Tải lên, Phê duyệt, Từ chối, Tra cứu và Kiểm tra đơn hàng.
 */
@ApplicationScoped
public class PrescriptionService {

    private static final Logger LOG = Logger.getLogger(PrescriptionService.class);

    @Inject
    PrescriptionRepository prescriptionRepo;

    @Inject
    PrescriptionItemRepository itemRepo;

    @Inject
    PrescriptionOrderLinkRepository linkRepo;

    @Inject
    MinioStorageService minioStorageService;

    @Inject
    PrescriptionKafkaProducer kafkaProducer;

    @Inject
    PrescriptionMapper mapper;

    @Inject
    JsonWebToken jwt;

    @Inject
    SecurityIdentity securityIdentity;

    public record PrescriptionImageData(InputStream stream, String contentType) {}

    /**
     * Khách hàng tải lên ảnh đơn thuốc mới
     */
    @Transactional
    public PrescriptionResponse createPrescription(PrescriptionUploadForm form) {
        UUID customerId = getUserIdFromJwt();
        String customerName = jwt.getClaim("name");
        String customerEmail = jwt.getClaim("email");

        // Tải ảnh lên MinIO và xác thực định dạng JPG/PNG <= 5MB
        MinioStorageService.UploadResult uploadResult = minioStorageService.uploadPrescriptionImage(form.image);

        Prescription entity = new Prescription();
        entity.setCustomerId(customerId);
        entity.setCustomerName(customerName);
        entity.setCustomerEmail(customerEmail);
        entity.setImagePath(uploadResult.objectKey());
        entity.setImageContentType(uploadResult.contentType());
        entity.setImageSizeBytes(uploadResult.sizeBytes());
        entity.setNotes(form.notes != null ? form.notes.trim() : null);
        entity.setStatus(PrescriptionStatus.CHO_DUYET);

        prescriptionRepo.persist(entity);
        LOG.infof("Khách hàng %s đã gửi đơn thuốc mới %s (ảnh: %s)", customerId, entity.getId(), uploadResult.objectKey());

        return mapper.toResponse(entity);
    }

    /**
     * Khách hàng xem danh sách đơn thuốc của chính mình
     */
    public PrescriptionPageResponse getMyPrescriptions(PrescriptionStatus status, int page, int size) {
        UUID customerId = getUserIdFromJwt();
        int safePage = Math.max(page, 0);
        int safeSize = (size > 0 && size <= 100) ? size : 20;

        List<Prescription> list = prescriptionRepo.findByCustomerId(customerId, status, safePage, safeSize);
        long total = prescriptionRepo.countByCustomerId(customerId, status);
        int totalPages = (int) Math.ceil((double) total / safeSize);

        List<PrescriptionResponse> responses = list.stream().map(mapper::toResponse).toList();
        return new PrescriptionPageResponse(responses, safePage, safeSize, total, totalPages);
    }

    /**
     * Dược sĩ và Admin lấy danh sách đơn thuốc đang chờ duyệt (ưu tiên đơn gửi trước duyệt trước)
     */
    public PrescriptionPageResponse getPendingPrescriptions(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = (size > 0 && size <= 100) ? size : 20;

        List<Prescription> list = prescriptionRepo.findPendingPrescriptions(safePage, safeSize);
        long total = prescriptionRepo.countPending();
        int totalPages = (int) Math.ceil((double) total / safeSize);

        List<PrescriptionResponse> responses = list.stream().map(mapper::toResponse).toList();
        return new PrescriptionPageResponse(responses, safePage, safeSize, total, totalPages);
    }

    /**
     * Xem thông tin chi tiết một đơn thuốc kèm danh mục thuốc được duyệt
     */
    public PrescriptionResponse getPrescriptionById(UUID id) {
        Prescription prescription = prescriptionRepo.findByIdWithItems(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn thuốc với mã: " + id));

        assertCanViewPrescription(prescription);

        return mapper.toResponse(prescription);
    }

    /**
     * Tải luồng dữ liệu hình ảnh đơn thuốc từ MinIO
     */
    public PrescriptionImageData getPrescriptionImage(UUID id) {
        Prescription prescription = prescriptionRepo.findByIdOptional(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn thuốc với mã: " + id));

        assertCanViewPrescription(prescription);

        InputStream stream = minioStorageService.getPrescriptionImageStream(prescription.getImagePath());
        return new PrescriptionImageData(stream, prescription.getImageContentType());
    }

    /**
     * Dược sĩ duyệt đơn thuốc và danh mục thuốc được phép mua
     */
    @Transactional
    public PrescriptionResponse approvePrescription(UUID id, ApprovePrescriptionRequest request) {
        Prescription prescription = prescriptionRepo.findByIdWithItems(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn thuốc với mã: " + id));

        // Kiểm tra xem đơn đã xử lý chưa
        if (prescription.getStatus() != PrescriptionStatus.CHO_DUYET) {
            throw ApiException.conflict(
                    "PRESCRIPTION_ALREADY_PROCESSED",
                    "Đơn thuốc đã được xử lý trước đó (trạng thái hiện tại: " + prescription.getStatus() + ")"
            );
        }

        if (request.items() == null || request.items().isEmpty()) {
            throw ApiException.badRequest("EMPTY_APPROVED_ITEMS", "Danh sách thuốc được phê duyệt không được để trống");
        }

        UUID pharmacistId = getUserIdFromJwt();
        String pharmacistName = jwt.getClaim("name");

        Instant expiresAt = request.expiresAt() != null
                ? request.expiresAt()
                : Instant.now().plus(Duration.ofDays(30));

        prescription.setStatus(PrescriptionStatus.DA_DUYET);
        prescription.setPharmacistId(pharmacistId);
        prescription.setPharmacistName(pharmacistName);
        prescription.setReviewedAt(Instant.now());
        prescription.setExpiresAt(expiresAt);
        prescription.setRejectionReason(null);

        // Lưu danh mục thuốc được duyệt
        for (ApproveItemRequest itemReq : request.items()) {
            PrescriptionItem item = new PrescriptionItem();
            item.setMedicineId(itemReq.medicineId());
            item.setMedicineName(itemReq.medicineName());
            item.setMaxAllowedQuantity(itemReq.maxAllowedQuantity());
            prescription.addItem(item);
        }

        prescriptionRepo.persist(prescription);
        LOG.infof("Dược sĩ %s đã duyệt đơn thuốc %s với %d loại thuốc (hết hạn: %s)",
                pharmacistId, prescription.getId(), request.items().size(), expiresAt);

        // Phát sự kiện Kafka prescription.reviewed
        List<PrescriptionReviewedEvent.ApprovedItemEvent> eventItems = request.items().stream()
                .map(it -> new PrescriptionReviewedEvent.ApprovedItemEvent(it.medicineId(), it.maxAllowedQuantity()))
                .toList();

        PrescriptionReviewedEvent event = new PrescriptionReviewedEvent(
                UUID.randomUUID(),
                "prescription.reviewed",
                Instant.now(),
                UUID.randomUUID().toString(),
                prescription.getId(),
                prescription.getCustomerId(),
                prescription.getCustomerEmail(),
                prescription.getCustomerName(),
                pharmacistId,
                PrescriptionStatus.DA_DUYET.name(),
                null,
                expiresAt,
                eventItems
        );
        kafkaProducer.sendPrescriptionReviewed(event);

        return mapper.toResponse(prescription);
    }

    /**
     * Dược sĩ từ chối đơn thuốc (bắt buộc có lý do)
     */
    @Transactional
    public PrescriptionResponse rejectPrescription(UUID id, RejectPrescriptionRequest request) {
        Prescription prescription = prescriptionRepo.findByIdWithItems(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn thuốc với mã: " + id));

        // Kiểm tra xem đơn đã xử lý chưa
        if (prescription.getStatus() != PrescriptionStatus.CHO_DUYET) {
            throw ApiException.conflict(
                    "PRESCRIPTION_ALREADY_PROCESSED",
                    "Đơn thuốc đã được xử lý trước đó (trạng thái hiện tại: " + prescription.getStatus() + ")"
            );
        }

        if (request.reason() == null || request.reason().trim().isBlank()) {
            throw ApiException.badRequest("REJECTION_REASON_REQUIRED", "Bắt buộc phải ghi rõ lý do từ chối đơn thuốc");
        }

        UUID pharmacistId = getUserIdFromJwt();
        String pharmacistName = jwt.getClaim("name");

        prescription.setStatus(PrescriptionStatus.TU_CHOI);
        prescription.setPharmacistId(pharmacistId);
        prescription.setPharmacistName(pharmacistName);
        prescription.setReviewedAt(Instant.now());
        prescription.setRejectionReason(request.reason().trim());

        prescriptionRepo.persist(prescription);
        LOG.infof("Dược sĩ %s đã từ chối đơn thuốc %s vì lý do: %s",
                pharmacistId, prescription.getId(), request.reason());

        // Phát sự kiện Kafka prescription.reviewed
        PrescriptionReviewedEvent event = new PrescriptionReviewedEvent(
                UUID.randomUUID(),
                "prescription.reviewed",
                Instant.now(),
                UUID.randomUUID().toString(),
                prescription.getId(),
                prescription.getCustomerId(),
                prescription.getCustomerEmail(),
                prescription.getCustomerName(),
                pharmacistId,
                PrescriptionStatus.TU_CHOI.name(),
                request.reason().trim(),
                null,
                Collections.emptyList()
        );
        kafkaProducer.sendPrescriptionReviewed(event);

        return mapper.toResponse(prescription);
    }

    /**
     * API nội bộ cho order-service kiểm tra tính hợp lệ của đơn thuốc cho các thuốc kê đơn (ETC)
     */
    public ValidatePrescriptionResponse validateForOrder(UUID id, ValidatePrescriptionRequest request) {
        Prescription prescription = prescriptionRepo.findByIdWithItems(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn thuốc với mã: " + id));

        // 1. Kiểm tra chủ sở hữu đơn thuốc
        if (!prescription.getCustomerId().equals(request.customerId())) {
            throw ApiException.forbidden("Đơn thuốc không thuộc về khách hàng đang thực hiện đặt hàng");
        }

        List<String> violations = new ArrayList<>();

        // 2. Kiểm tra trạng thái đơn
        if (prescription.getStatus() != PrescriptionStatus.DA_DUYET) {
            violations.add("Đơn thuốc chưa được duyệt (trạng thái hiện tại: " + prescription.getStatus() + ")");
        }

        // 3. Kiểm tra ngày hết hạn
        if (prescription.getExpiresAt() != null && prescription.getExpiresAt().isBefore(Instant.now())) {
            violations.add("Đơn thuốc đã hết hiệu lực vào ngày: " + prescription.getExpiresAt());
        }

        // 4. Kiểm tra danh mục thuốc và số lượng
        Map<String, Integer> approvedMap = prescription.getItems().stream()
                .collect(Collectors.toMap(
                        PrescriptionItem::getMedicineId,
                        PrescriptionItem::getMaxAllowedQuantity,
                        (existing, replacement) -> existing
                ));

        for (ValidateItemRequest reqItem : request.items()) {
            Integer maxAllowed = approvedMap.get(reqItem.medicineId());
            if (maxAllowed == null) {
                violations.add("Thuốc có mã '" + reqItem.medicineId() + "' không nằm trong danh mục thuốc được duyệt");
            } else if (reqItem.quantity() > maxAllowed) {
                violations.add(String.format("Số lượng đặt mua thuốc '%s' là %d, vượt quá số lượng tối đa được phép là %d",
                        reqItem.medicineId(), reqItem.quantity(), maxAllowed));
            }
        }

        if (!violations.isEmpty()) {
            throw ApiException.unprocessable("PRESCRIPTION_INVALID", "Đơn thuốc không hợp lệ để mua danh sách thuốc này", violations);
        }

        return new ValidatePrescriptionResponse(
                prescription.getId(),
                prescription.getCustomerId(),
                true,
                prescription.getStatus(),
                prescription.getExpiresAt(),
                Collections.emptyList()
        );
    }

    /**
     * API nội bộ cho order-service gắn đơn thuốc vào đơn hàng (Idempotent)
     */
    @Transactional
    public LinkOrderResponse linkOrder(UUID id, LinkOrderRequest request) {
        Prescription prescription = prescriptionRepo.findByIdOptional(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn thuốc với mã: " + id));

        // Kiểm tra quyền sở hữu
        if (!prescription.getCustomerId().equals(request.customerId())) {
            throw ApiException.forbidden("Đơn thuốc không thuộc về khách hàng đang thực hiện đặt hàng");
        }

        // Kiểm tra xem đơn hàng này đã từng được gắn với đơn thuốc này chưa (Xử lý Idempotent)
        Optional<PrescriptionOrderLink> existingByOrder = linkRepo.findByOrderId(request.orderId());
        if (existingByOrder.isPresent()) {
            PrescriptionOrderLink existing = existingByOrder.get();
            if (existing.getPrescription().getId().equals(id)) {
                return new LinkOrderResponse(existing.getId(), id, request.orderId(), existing.getLinkedAt());
            } else {
                throw ApiException.conflict(
                        "ORDER_ALREADY_LINKED_TO_OTHER_PRESCRIPTION",
                        "Đơn hàng này đã được gắn với một đơn thuốc khác trước đó"
                );
            }
        }

        // Kiểm tra xem đơn thuốc này đã từng dùng cho đơn hàng khác chưa (quy tắc 1 đơn thuốc chỉ dùng 1 lần)
        Optional<PrescriptionOrderLink> existingByPrescription = linkRepo.findByPrescriptionId(id);
        if (existingByPrescription.isPresent()) {
            throw ApiException.conflict(
                    "PRESCRIPTION_ALREADY_USED",
                    "Đơn thuốc này đã được sử dụng cho một đơn hàng trước đó (Mã đơn: " + existingByPrescription.get().getOrderId() + ")"
            );
        }

        PrescriptionOrderLink link = new PrescriptionOrderLink();
        link.setPrescription(prescription);
        link.setOrderId(request.orderId());
        link.setCustomerId(request.customerId());

        linkRepo.persist(link);
        LOG.infof("Đã liên kết đơn thuốc %s với đơn hàng %s", id, request.orderId());

        return new LinkOrderResponse(link.getId(), id, request.orderId(), link.getLinkedAt());
    }

    /**
     * Kiểm tra người dùng hiện tại có quyền xem đơn thuốc không
     * - DUOC_SI và ADMIN được xem mọi đơn
     * - KHACH_HANG chỉ được xem đơn do chính mình tạo
     */
    private void assertCanViewPrescription(Prescription prescription) {
        boolean isPharmacistOrAdmin = securityIdentity.hasRole("DUOC_SI") || securityIdentity.hasRole("ADMIN");
        if (!isPharmacistOrAdmin) {
            UUID currentUserId = getUserIdFromJwt();
            if (!prescription.getCustomerId().equals(currentUserId)) {
                throw ApiException.forbidden("Bạn không có quyền xem đơn thuốc của người dùng khác");
            }
        }
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

