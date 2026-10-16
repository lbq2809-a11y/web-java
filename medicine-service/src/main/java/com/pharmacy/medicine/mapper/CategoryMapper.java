package com.pharmacy.medicine.mapper;

import com.pharmacy.medicine.document.CategoryDocument;
import com.pharmacy.medicine.dto.CategoryRequest;
import com.pharmacy.medicine.dto.CategoryResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

/**
 * Mapper chuyển đổi giữa CategoryDocument và các DTO danh mục thuốc.
 */
@ApplicationScoped
public class CategoryMapper {

    public CategoryDocument toDocument(CategoryRequest req) {
        CategoryDocument doc = new CategoryDocument();
        doc.code = req.code.trim().toUpperCase();
        doc.name = req.name.trim();
        doc.description = req.description != null ? req.description.trim() : null;
        doc.active = req.active == null || req.active;
        Instant now = Instant.now();
        doc.createdAt = now;
        doc.updatedAt = now;
        return doc;
    }

    public void updateDocument(CategoryDocument doc, CategoryRequest req) {
        doc.code = req.code.trim().toUpperCase();
        doc.name = req.name.trim();
        doc.description = req.description != null ? req.description.trim() : null;
        if (req.active != null) {
            doc.active = req.active;
        }
        doc.updatedAt = Instant.now();
    }

    public CategoryResponse toResponse(CategoryDocument doc) {
        CategoryResponse res = new CategoryResponse();
        res.id = doc.id != null ? doc.id.toHexString() : null;
        res.code = doc.code;
        res.name = doc.name;
        res.description = doc.description;
        res.active = doc.active;
        res.createdAt = doc.createdAt;
        res.updatedAt = doc.updatedAt;
        return res;
    }
}

