package com.pharmacy.consultation.dto;

/**
 * Thuốc gợi ý khi câu hỏi khớp nhiều loại thuốc (ví dụ chung hoạt chất).
 */
public class SuggestedMedicineDto {

    private String medicineId;
    private String name;
    private String activeIngredient;
    private String prescriptionType;

    public SuggestedMedicineDto() {
    }

    public SuggestedMedicineDto(String medicineId, String name, String activeIngredient, String prescriptionType) {
        this.medicineId = medicineId;
        this.name = name;
        this.activeIngredient = activeIngredient;
        this.prescriptionType = prescriptionType;
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

    public String getActiveIngredient() {
        return activeIngredient;
    }

    public void setActiveIngredient(String activeIngredient) {
        this.activeIngredient = activeIngredient;
    }

    public String getPrescriptionType() {
        return prescriptionType;
    }

    public void setPrescriptionType(String prescriptionType) {
        this.prescriptionType = prescriptionType;
    }
}

