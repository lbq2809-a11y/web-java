package com.pharmacy.medicine.mapper;

import com.pharmacy.medicine.document.ReviewDocument;
import com.pharmacy.medicine.dto.ReviewRequest;
import com.pharmacy.medicine.dto.ReviewResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

/**
 * Mapper chuyển đổi giữa ReviewDocument và các DTO đánh giá thuốc.
 */
@ApplicationScoped
public class ReviewMapper {

    public ReviewDocument toDocument(String medicineId, String userId, String userFullName, ReviewRequest req) {
        ReviewDocument doc = new ReviewDocument();
        doc.medicineId = medicineId;
        doc.userId = userId;
        doc.userFullName = userFullName;
        doc.rating = req.rating;
        doc.comment = req.comment.trim();
        Instant now = Instant.now();
        doc.createdAt = now;
        doc.updatedAt = now;
        return doc;
    }

    public ReviewResponse toResponse(ReviewDocument doc) {
        ReviewResponse res = new ReviewResponse();
        res.id = doc.id != null ? doc.id.toHexString() : null;
        res.medicineId = doc.medicineId;
        res.userId = doc.userId;
        res.userFullName = doc.userFullName;
        res.rating = doc.rating;
        res.comment = doc.comment;
        res.createdAt = doc.createdAt;
        res.updatedAt = doc.updatedAt;
        return res;
    }
}

