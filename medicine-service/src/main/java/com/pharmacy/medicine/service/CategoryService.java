package com.pharmacy.medicine.service;

import com.pharmacy.medicine.document.CategoryDocument;
import com.pharmacy.medicine.dto.CategoryRequest;
import com.pharmacy.medicine.dto.CategoryResponse;
import com.pharmacy.medicine.exception.ApiException;
import com.pharmacy.medicine.mapper.CategoryMapper;
import com.pharmacy.medicine.repository.CategoryRepository;
import com.pharmacy.medicine.repository.MedicineRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.List;

/**
 * Xử lý nghiệp vụ cho danh mục nhóm thuốc (Categories).
 */
@ApplicationScoped
public class CategoryService {

    @Inject
    CategoryRepository categoryRepository;

    @Inject
    MedicineRepository medicineRepository;

    @Inject
    CategoryMapper categoryMapper;

    public List<CategoryResponse> listCategories(boolean includeInactive) {
        return categoryRepository.listCategories(includeInactive)
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    public CategoryResponse getCategoryById(String id) {
        CategoryDocument doc = findCategoryOrThrow(id);
        return categoryMapper.toResponse(doc);
    }

    public CategoryResponse createCategory(CategoryRequest request) {
        String normalizedCode = request.code.trim().toUpperCase();
        if (categoryRepository.findByCode(normalizedCode).isPresent()) {
            throw new ApiException(
                    "CATEGORY_CODE_EXISTS",
                    "Mã danh mục '" + normalizedCode + "' đã tồn tại",
                    Response.Status.CONFLICT
            );
        }

        CategoryDocument doc = categoryMapper.toDocument(request);
        categoryRepository.persist(doc);
        return categoryMapper.toResponse(doc);
    }

    public CategoryResponse updateCategory(String id, CategoryRequest request) {
        CategoryDocument existing = findCategoryOrThrow(id);
        String normalizedCode = request.code.trim().toUpperCase();

        categoryRepository.findByCode(normalizedCode).ifPresent(other -> {
            if (!other.id.equals(existing.id)) {
                throw new ApiException(
                        "CATEGORY_CODE_EXISTS",
                        "Mã danh mục '" + normalizedCode + "' đã được sử dụng bởi danh mục khác",
                        Response.Status.CONFLICT
                );
            }
        });

        String oldName = existing.name;
        categoryMapper.updateDocument(existing, request);
        categoryRepository.update(existing);

        // Đồng bộ tên danh mục (Extended Reference) sang các thuốc thuộc danh mục nếu tên thay đổi
        if (!oldName.equals(existing.name)) {
            medicineRepository.updateCategoryNameByCategoryId(existing.id.toHexString(), existing.name);
        }

        return categoryMapper.toResponse(existing);
    }

    public void deleteCategory(String id) {
        CategoryDocument existing = findCategoryOrThrow(id);
        long activeMedicines = medicineRepository.countActiveByCategoryId(existing.id.toHexString());
        if (activeMedicines > 0) {
            throw new ApiException(
                    "CATEGORY_IN_USE",
                    "Không thể xóa danh mục đang có " + activeMedicines + " thuốc đang hoạt động",
                    Response.Status.CONFLICT
            );
        }

        existing.active = false;
        existing.updatedAt = Instant.now();
        categoryRepository.update(existing);
    }

    public CategoryDocument findCategoryOrThrow(String id) {
        if (id == null || !ObjectId.isValid(id.trim())) {
            throw new ApiException(
                    "INVALID_CATEGORY_ID",
                    "Định dạng ID danh mục không hợp lệ: " + id,
                    Response.Status.BAD_REQUEST
            );
        }
        return categoryRepository.findByObjectId(new ObjectId(id.trim()))
                .orElseThrow(() -> new ApiException(
                        "CATEGORY_NOT_FOUND",
                        "Không tìm thấy danh mục thuốc với ID: " + id,
                        Response.Status.NOT_FOUND
                ));
    }
}

