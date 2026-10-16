package com.pharmacy.medicine.repository;

import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.pharmacy.medicine.document.ReviewDocument;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.mongodb.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.Optional;

/**
 * Repository thao tác với collection "reviews" trong MongoDB.
 * Tính điểm đánh giá trung bình (averageRating) và tổng số lượt đánh giá (reviewCount)
 * bằng MongoDB Aggregation Pipeline trực tiếp trên collection reviews.
 */
@ApplicationScoped
public class ReviewRepository implements PanacheMongoRepository<ReviewDocument> {

    public Optional<ReviewDocument> findByMedicineAndUser(String medicineId, String userId) {
        return find("medicineId = ?1 and userId = ?2", medicineId, userId).firstResultOptional();
    }

    public PanacheQuery<ReviewDocument> findByMedicineId(String medicineId, int page, int size) {
        return find("medicineId", Sort.by("createdAt").descending(), medicineId)
                .page(Page.of(page, size));
    }

    /**
     * Kết quả tính toán thống kê đánh giá từ MongoDB Aggregation Pipeline.
     */
    public record RatingStats(double averageRating, int reviewCount) {
    }

    /**
     * Sử dụng MongoDB Aggregation ($match + $group với $avg và $sum) để tính lại chính xác
     * điểm trung bình và tổng số đánh giá của một thuốc, không cộng dồn thủ công.
     */
    public RatingStats computeRatingStats(String medicineId) {
        List<Bson> pipeline = List.of(
                Aggregates.match(Filters.eq("medicineId", medicineId)),
                Aggregates.group(
                        "$medicineId",
                        Accumulators.avg("avgRating", "$rating"),
                        Accumulators.sum("totalCount", 1)
                )
        );

        Document result = mongoCollection().aggregate(pipeline, Document.class).first();
        if (result == null) {
            return new RatingStats(0.0, 0);
        }

        Number avgNum = result.get("avgRating", Number.class);
        Number countNum = result.get("totalCount", Number.class);

        double rawAvg = avgNum != null ? avgNum.doubleValue() : 0.0;
        double roundedAvg = Math.round(rawAvg * 10.0) / 10.0;
        int count = countNum != null ? countNum.intValue() : 0;

        return new RatingStats(roundedAvg, count);
    }
}

