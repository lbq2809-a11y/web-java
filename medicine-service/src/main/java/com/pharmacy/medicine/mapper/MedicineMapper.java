package com.pharmacy.medicine.mapper;

import com.pharmacy.medicine.document.CategoryDocument;
import com.pharmacy.medicine.document.MedicineDocument;
import com.pharmacy.medicine.dto.MedicineRequest;
import com.pharmacy.medicine.dto.MedicineResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

/**
 * Mapper chuyển đổi giữa MedicineDocument và các DTO thuốc.
 */
@ApplicationScoped
public class MedicineMapper {

    public MedicineDocument toDocument(MedicineRequest req, CategoryDocument category) {
        MedicineDocument doc = new MedicineDocument();
        doc.code = req.code.trim().toUpperCase();
        doc.name = req.name.trim();
        doc.activeIngredient = req.activeIngredient.trim();
        doc.manufacturer = req.manufacturer.trim();
        doc.unit = req.unit.trim();
        doc.price = req.price;
        doc.imageUrl = req.imageUrl != null ? req.imageUrl.trim() : null;
        doc.indications = req.indications.trim();
        doc.contraindications = req.contraindications.trim();
        doc.dosage = req.dosage.trim();
        doc.prescriptionType = req.prescriptionType;
        doc.categoryId = category.id.toHexString();
        doc.categoryName = category.name;
        doc.active = req.active == null || req.active;
        doc.averageRating = 0.0;
        doc.reviewCount = 0;
        Instant now = Instant.now();
        doc.createdAt = now;
        doc.updatedAt = now;
        return doc;
    }

    public void updateDocument(MedicineDocument doc, MedicineRequest req, CategoryDocument category) {
        doc.code = req.code.trim().toUpperCase();
        doc.name = req.name.trim();
        doc.activeIngredient = req.activeIngredient.trim();
        doc.manufacturer = req.manufacturer.trim();
        doc.unit = req.unit.trim();
        doc.price = req.price;
        doc.imageUrl = req.imageUrl != null ? req.imageUrl.trim() : null;
        doc.indications = req.indications.trim();
        doc.contraindications = req.contraindications.trim();
        doc.dosage = req.dosage.trim();
        doc.prescriptionType = req.prescriptionType;
        doc.categoryId = category.id.toHexString();
        doc.categoryName = category.name;
        if (req.active != null) {
            doc.active = req.active;
        }
        doc.updatedAt = Instant.now();
    }

    public MedicineResponse toResponse(MedicineDocument doc) {
        MedicineResponse res = new MedicineResponse();
        res.id = doc.id != null ? doc.id.toHexString() : null;
        res.code = doc.code;
        res.name = doc.name;
        res.activeIngredient = doc.activeIngredient;
        res.manufacturer = doc.manufacturer;
        res.unit = doc.unit;
        res.price = doc.price;
        res.imageUrl = doc.imageUrl;
        res.indications = doc.indications;
        res.contraindications = doc.contraindications;
        res.dosage = doc.dosage;
        res.prescriptionType = doc.prescriptionType;
        res.categoryId = doc.categoryId;
        res.categoryName = doc.categoryName;
        res.active = doc.active;
        res.averageRating = doc.averageRating;
        res.reviewCount = doc.reviewCount;
        res.createdAt = doc.createdAt;
        res.updatedAt = doc.updatedAt;
        return res;
    }
}

