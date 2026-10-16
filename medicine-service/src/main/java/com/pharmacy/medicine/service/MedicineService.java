package com.pharmacy.medicine.service;

import com.pharmacy.medicine.document.CategoryDocument;
import com.pharmacy.medicine.document.MedicineDocument;
import com.pharmacy.medicine.document.PrescriptionType;
import com.pharmacy.medicine.dto.MedicineRequest;
import com.pharmacy.medicine.dto.MedicineResponse;
import com.pharmacy.medicine.dto.PagedResponse;
import com.pharmacy.medicine.exception.ApiException;
import com.pharmacy.medicine.mapper.MedicineMapper;
import com.pharmacy.medicine.repository.MedicineRepository;
import io.quarkus.mongodb.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Xử lý nghiệp vụ quản lý thuốc, tìm kiếm/lọc/phân trang và truy vấn theo lô (batch).
 */
@ApplicationScoped
public class MedicineService {

    private static final int MAX_BATCH_IDS = 50;

    @Inject
    MedicineRepository medicineRepository;

    @Inject
    CategoryService categoryService;

    @Inject
    MedicineMapper medicineMapper;

    public PagedResponse<MedicineResponse> searchMedicines(
            String keyword,
            String categoryId,
            PrescriptionType prescriptionType,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            boolean includeInactive,
            String sortBy,
            String sortDir,
            int page,
            int size
    ) {
        if (page < 0) {
            throw new ApiException("INVALID_PAGE", "Số trang (page) phải lớn hơn hoặc bằng 0", Response.Status.BAD_REQUEST);
        }
        if (size <= 0 || size > 100) {
            throw new ApiException("INVALID_PAGE_SIZE", "Kích thước trang (size) phải từ 1 đến 100", Response.Status.BAD_REQUEST);
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new ApiException("INVALID_PRICE_RANGE", "Giá tối thiểu (minPrice) không được lớn hơn giá tối đa (maxPrice)", Response.Status.BAD_REQUEST);
        }

        PanacheQuery<MedicineDocument> query = medicineRepository.searchMedicines(
                keyword, categoryId, prescriptionType, minPrice, maxPrice, includeInactive, sortBy, sortDir, page, size
        );

        long totalItems = query.count();
        int totalPages = query.pageCount();
        List<MedicineResponse> items = query.list()
                .stream()
                .map(medicineMapper::toResponse)
                .toList();

        return new PagedResponse<>(items, page, size, totalItems, totalPages);
    }

    public MedicineResponse getMedicineById(String id, boolean allowInactive) {
        MedicineDocument doc = findMedicineOrThrow(id);
        if (!doc.active && !allowInactive) {
            throw new ApiException(
                    "MEDICINE_NOT_FOUND",
                    "Không tìm thấy thuốc với ID: " + id,
                    Response.Status.NOT_FOUND
            );
        }
        return medicineMapper.toResponse(doc);
    }

    /**
     * Lấy nhiều thuốc cùng lúc theo chuỗi ID phân tách bằng dấu phẩy (tối đa 50 ID).
     * Phục vụ order-service / prescription-service lấy thông tin thuốc hàng loạt trong 1 lần gọi.
     */
    public List<MedicineResponse> getMedicinesBatch(String idsParam) {
        if (idsParam == null || idsParam.isBlank()) {
            throw new ApiException(
                    "EMPTY_BATCH_IDS",
                    "Tham số 'ids' không được để trống",
                    Response.Status.BAD_REQUEST
            );
        }

        Set<String> uniqueRawIds = Arrays.stream(idsParam.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (uniqueRawIds.isEmpty()) {
            throw new ApiException(
                    "EMPTY_BATCH_IDS",
                    "Tham số 'ids' không chứa ID hợp lệ",
                    Response.Status.BAD_REQUEST
            );
        }

        if (uniqueRawIds.size() > MAX_BATCH_IDS) {
            throw new ApiException(
                    "BATCH_SIZE_EXCEEDED",
                    "Chỉ cho phép truy vấn tối đa " + MAX_BATCH_IDS + " thuốc trong một lần gọi batch",
                    Response.Status.BAD_REQUEST
            );
        }

        List<ObjectId> objectIds = new ArrayList<>(uniqueRawIds.size());
        List<String> invalidIds = new ArrayList<>();
        for (String rawId : uniqueRawIds) {
            if (!ObjectId.isValid(rawId)) {
                invalidIds.add(rawId);
            } else {
                objectIds.add(new ObjectId(rawId));
            }
        }

        if (!invalidIds.isEmpty()) {
            throw new ApiException(
                    "INVALID_MEDICINE_ID",
                    "Danh sách chứa ID thuốc không đúng định dạng ObjectId 24 ký tự hex",
                    Response.Status.BAD_REQUEST,
                    invalidIds
            );
        }

        Map<String, MedicineDocument> foundById = medicineRepository.findByIds(objectIds)
                .stream()
                .collect(Collectors.toMap(doc -> doc.id.toHexString(), Function.identity()));

        // Giữ nguyên thứ tự các ID được yêu cầu từ client
        List<MedicineResponse> responses = new ArrayList<>();
        for (String rawId : uniqueRawIds) {
            MedicineDocument doc = foundById.get(rawId);
            if (doc != null) {
                responses.add(medicineMapper.toResponse(doc));
            }
        }
        return responses;
    }

    public MedicineResponse createMedicine(MedicineRequest request) {
        String normalizedCode = request.code.trim().toUpperCase();
        if (medicineRepository.findByCode(normalizedCode).isPresent()) {
            throw new ApiException(
                    "MEDICINE_CODE_EXISTS",
                    "Mã thuốc '" + normalizedCode + "' đã tồn tại",
                    Response.Status.CONFLICT
            );
        }

        CategoryDocument category = categoryService.findCategoryOrThrow(request.categoryId);
        MedicineDocument doc = medicineMapper.toDocument(request, category);
        medicineRepository.persist(doc);
        return medicineMapper.toResponse(doc);
    }

    public MedicineResponse updateMedicine(String id, MedicineRequest request) {
        MedicineDocument existing = findMedicineOrThrow(id);
        String normalizedCode = request.code.trim().toUpperCase();

        medicineRepository.findByCode(normalizedCode).ifPresent(other -> {
            if (!other.id.equals(existing.id)) {
                throw new ApiException(
                        "MEDICINE_CODE_EXISTS",
                        "Mã thuốc '" + normalizedCode + "' đã được sử dụng bởi thuốc khác",
                        Response.Status.CONFLICT
                );
            }
        });

        CategoryDocument category = categoryService.findCategoryOrThrow(request.categoryId);
        medicineMapper.updateDocument(existing, request, category);
        medicineRepository.update(existing);
        return medicineMapper.toResponse(existing);
    }

    public void deleteMedicine(String id) {
        MedicineDocument existing = findMedicineOrThrow(id);
        existing.active = false;
        existing.updatedAt = Instant.now();
        medicineRepository.update(existing);
    }

    public MedicineDocument findMedicineOrThrow(String id) {
        if (id == null || !ObjectId.isValid(id.trim())) {
            throw new ApiException(
                    "INVALID_MEDICINE_ID",
                    "Định dạng ID thuốc không hợp lệ: " + id,
                    Response.Status.BAD_REQUEST
            );
        }
        return medicineRepository.findByObjectId(new ObjectId(id.trim()))
                .orElseThrow(() -> new ApiException(
                        "MEDICINE_NOT_FOUND",
                        "Không tìm thấy thuốc với ID: " + id,
                        Response.Status.NOT_FOUND
                ));
    }
}

