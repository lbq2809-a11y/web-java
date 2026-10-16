package com.pharmacy.medicine.dto;

import com.pharmacy.medicine.document.PrescriptionType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO trả thông tin chi tiết thuốc về cho client và các service khác (order-service, prescription-service).
 */
public class MedicineResponse {
    public String id;
    public String code;
    public String name;
    public String activeIngredient;
    public String manufacturer;
    public String unit;
    public BigDecimal price;
    public String imageUrl;
    public String indications;
    public String contraindications;
    public String dosage;
    public PrescriptionType prescriptionType;
    public String categoryId;
    public String categoryName;
    public boolean active;
    public double averageRating;
    public int reviewCount;
    public Instant createdAt;
    public Instant updatedAt;
}

