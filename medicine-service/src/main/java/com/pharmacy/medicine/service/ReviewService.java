package com.pharmacy.medicine.service;

import com.mongodb.MongoWriteException;
import com.pharmacy.medicine.document.MedicineDocument;
import com.pharmacy.medicine.document.ReviewDocument;
import com.pharmacy.medicine.dto.PagedResponse;
import com.pharmacy.medicine.dto.ReviewRequest;
import com.pharmacy.medicine.dto.ReviewResponse;
import com.pharmacy.medicine.exception.ApiException;
import com.pharmacy.medicine.mapper.ReviewMapper;
import com.pharmacy.medicine.repository.MedicineRepository;
import com.pharmacy.medicine.repository.ReviewRepository;
import io.quarkus.mongodb.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.List;

/**
 * Xử lý nghiệp vụ đánh giá thuốc (Reviews).
 * Đảm bảo mỗi người dùng chỉ được tạo tối đa 1 đánh giá cho mỗi thuốc và tính lại điểm trung bình
 * bằng MongoDB Aggregation Pipeline trên collection reviews.
 */
@ApplicationScoped
public class ReviewService {

    @Inject
    ReviewRepository reviewRepository;

    @Inject
    MedicineRepository medicineRepository;

    @Inject
    MedicineService medicineService;

    @Inject
    ReviewMapper reviewMapper;

    public PagedResponse<ReviewResponse> listReviewsByMedicine(String medicineId, int page, int size) {
        if (page < 0) {
            throw new ApiException("INVALID_PAGE", "Số trang (page) phải lớn hơn hoặc bằng 0", Response.Status.BAD_REQUEST);
        }
        if (size <= 0 || size > 100) {
            throw new ApiException("INVALID_PAGE_SIZE", "Kích thước trang (size) phải từ 1 đến 100", Response.Status.BAD_REQUEST);
        }

        // Kiểm tra thuốc tồn tại
        MedicineDocument medicine = medicineService.findMedicineOrThrow(medicineId);

        PanacheQuery<ReviewDocument> query = reviewRepository.findByMedicineId(medicine.id.toHexString(), page, size);
        long totalItems = query.count();
        int totalPages = query.pageCount();
        List<ReviewResponse> items = query.list()
                .stream()
                .map(reviewMapper::toResponse)
                .toList();

        return new PagedResponse<>(items, page, size, totalItems, totalPages);
    }

    public ReviewResponse createReview(String medicineId, String userId, String userFullName, ReviewRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new ApiException("UNAUTHORIZED", "Không xác định được định danh người dùng từ JWT", Response.Status.UNAUTHORIZED);
        }

        MedicineDocument medicine = medicineService.findMedicineOrThrow(medicineId);
        if (!medicine.active) {
            throw new ApiException("MEDICINE_NOT_FOUND", "Không tìm thấy thuốc với ID: " + medicineId, Response.Status.NOT_FOUND);
        }

        String hexMedicineId = medicine.id.toHexString();
        if (reviewRepository.findByMedicineAndUser(hexMedicineId, userId).isPresent()) {
            throw new ApiException(
                    "REVIEW_ALREADY_EXISTS",
                    "Bạn đã đánh giá sản phẩm thuốc này trước đó (mỗi tài khoản chỉ được đánh giá 1 lần cho mỗi thuốc)",
                    Response.Status.CONFLICT
            );
        }

        String resolvedName = (userFullName != null && !userFullName.isBlank()) ? userFullName : "Khách hàng";
        ReviewDocument review = reviewMapper.toDocument(hexMedicineId, userId, resolvedName, request);

        try {
            reviewRepository.persist(review);
        } catch (MongoWriteException e) {
            // Bắt lỗi vi phạm Compound Unique Index (medicineId, userId) trong trường hợp gửi đồng thời (race condition)
            if (e.getError() != null && e.getError().getCode() == 11000) {
                throw new ApiException(
                        "REVIEW_ALREADY_EXISTS",
                        "Bạn đã đánh giá sản phẩm thuốc này trước đó",
                        Response.Status.CONFLICT
                );
            }
            throw e;
        }

        // Tính lại averageRating và reviewCount bằng MongoDB Aggregation trên collection reviews (không cộng dồn thủ công)
        recalculateMedicineRating(medicine);

        return reviewMapper.toResponse(review);
    }

    /**
     * Chạy MongoDB Aggregation Pipeline trên collection reviews và cập nhật lại vào MedicineDocument.
     */
    public void recalculateMedicineRating(MedicineDocument medicine) {
        ReviewRepository.RatingStats stats = reviewRepository.computeRatingStats(medicine.id.toHexString());
        medicine.averageRating = stats.averageRating();
        medicine.reviewCount = stats.reviewCount();
        medicine.updatedAt = Instant.now();
        medicineRepository.update(medicine);
    }
}

