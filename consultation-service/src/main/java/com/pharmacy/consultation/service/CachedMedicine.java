package com.pharmacy.consultation.service;

import java.math.BigDecimal;

/**
 * Bản ghi thuốc được lưu đệm trong bộ nhớ RAM phục vụ nhận diện siêu tốc tại Tầng 1.
 */
public class CachedMedicine {

    private String medicineId;
    private String name;
    private String normalizedName;
    private String noDiacriticsName;

    private String activeIngredient;
    private String normalizedActiveIngredient;
    private String noDiacriticsActiveIngredient;

    private String prescriptionType;
    private String unit;
    private BigDecimal price;
    private String indications;
    private String contraindications;
    private String dosage;

    public CachedMedicine() {
    }

    public String getMedicineId() {
        return medicineId;
    }

    public void setMedicineId(String medicineId) {
        this.medicineId = medicineId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public String getNoDiacriticsName() {
        return noDiacriticsName;
    }

    public void setNoDiacriticsName(String noDiacriticsName) {
        this.noDiacriticsName = noDiacriticsName;
    }

    public String getActiveIngredient() {
        return activeIngredient;
    }

    public void setActiveIngredient(String activeIngredient) {
        this.activeIngredient = activeIngredient;
    }

    public String getNormalizedActiveIngredient() {
        return normalizedActiveIngredient;
    }

    public void setNormalizedActiveIngredient(String normalizedActiveIngredient) {
        this.normalizedActiveIngredient = normalizedActiveIngredient;
    }

    public String getNoDiacriticsActiveIngredient() {
        return noDiacriticsActiveIngredient;
    }

    public void setNoDiacriticsActiveIngredient(String noDiacriticsActiveIngredient) {
        this.noDiacriticsActiveIngredient = noDiacriticsActiveIngredient;
    }

    public String getPrescriptionType() {
        return prescriptionType;
    }

    public void setPrescriptionType(String prescriptionType) {
        this.prescriptionType = prescriptionType;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getIndications() {
        return indications;
    }

    public void setIndications(String indications) {
        this.indications = indications;
    }

    public String getContraindications() {
        return contraindications;
    }

    public void setContraindications(String contraindications) {
        this.contraindications = contraindications;
    }

    public String getDosage() {
        return dosage;
    }

    public void setDosage(String dosage) {
        this.dosage = dosage;
    }
}

