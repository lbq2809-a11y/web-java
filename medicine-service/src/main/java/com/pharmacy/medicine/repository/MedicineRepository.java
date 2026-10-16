package com.pharmacy.medicine.repository;

import com.pharmacy.medicine.document.MedicineDocument;
import com.pharmacy.medicine.document.PrescriptionType;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.mongodb.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Repository thao tác với collection "medicines" trong MongoDB.
 * Hỗ trợ tìm kiếm từ khóa, lọc đa tiêu chí, sắp xếp, phân trang và truy vấn hàng loạt (batch).
 */
@ApplicationScoped
public class MedicineRepository implements PanacheMongoRepository<MedicineDocument> {

    public Optional<MedicineDocument> findByCode(String code) {
        return find("code", code).firstResultOptional();
    }

    public Optional<MedicineDocument> findByObjectId(ObjectId id) {
        return findByIdOptional(id);
    }

    /**
     * Lấy nhiều thuốc cùng lúc theo danh sách ObjectId (phục vụ API /api/v1/medicines/batch).
     */
    public List<MedicineDocument> findByIds(List<ObjectId> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Document filter = new Document("_id", new Document("$in", ids));
        return find(filter).list();
    }

    /**
     * Đếm số lượng thuốc đang hoạt động thuộc một danh mục (để kiểm tra trước khi xóa danh mục).
     */
    public long countActiveByCategoryId(String categoryId) {
        return count("categoryId = ?1 and active = true", categoryId);
    }

    /**
     * Đồng bộ tên danh mục (Extended Reference) cho tất cả các thuốc thuộc danh mục khi danh mục đổi tên.
     */
    public void updateCategoryNameByCategoryId(String categoryId, String newCategoryName) {
        update("categoryName = ?1, updatedAt = ?2", newCategoryName, Instant.now())
                .where("categoryId", categoryId);
    }

    /**
     * Tìm kiếm, lọc, sắp xếp và phân trang danh sách thuốc.
     */
    public PanacheQuery<MedicineDocument> searchMedicines(
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
        List<Document> conditions = new ArrayList<>();

        if (!includeInactive) {
            conditions.add(new Document("active", true));
        }

        if (categoryId != null && !categoryId.isBlank()) {
            conditions.add(new Document("categoryId", categoryId.trim()));
        }

        if (prescriptionType != null) {
            conditions.add(new Document("prescriptionType", prescriptionType.name()));
        }

        if (minPrice != null || maxPrice != null) {
            Document priceRange = new Document();
            if (minPrice != null) {
                priceRange.append("$gte", new Decimal128(minPrice));
            }
            if (maxPrice != null) {
                priceRange.append("$lte", new Decimal128(maxPrice));
            }
            conditions.add(new Document("price", priceRange));
        }

        if (keyword != null && !keyword.isBlank()) {
            // Tìm kiếm không phân biệt hoa thường trên tên thuốc, mã thuốc, hoạt chất và chỉ định
            String escaped = Pattern.quote(keyword.trim());
            Document regex = new Document("$regex", escaped).append("$options", "i");
            conditions.add(new Document("$or", List.of(
                    new Document("name", regex),
                    new Document("code", regex),
                    new Document("activeIngredient", regex),
                    new Document("indications", regex)
            )));
        }

        Document queryDoc = conditions.isEmpty()
                ? new Document()
                : (conditions.size() == 1 ? conditions.getFirst() : new Document("$and", conditions));

        String resolvedSortField = switch (sortBy != null ? sortBy.trim() : "name") {
            case "price" -> "price";
            case "averageRating" -> "averageRating";
            case "createdAt" -> "createdAt";
            case "code" -> "code";
            default -> "name";
        };

        int sortOrder = "desc".equalsIgnoreCase(sortDir) ? -1 : 1;
        Document sortDoc = new Document(resolvedSortField, sortOrder);

        return find(queryDoc, sortDoc).page(Page.of(page, size));
    }
}
