package com.pharmacy.consultation.entity;

/**
 * Nguồn tri thức dẫn xuất cho câu trả lời của trợ lý AI.
 */
public class KnowledgeSource {

    private String medicineId;
    private String medicineName;
    private String section;

    public KnowledgeSource() {
    }

    public KnowledgeSource(String medicineId, String medicineName, String section) {
        this.medicineId = medicineId;
        this.medicineName = medicineName;
        this.section = section;
    }

    public String getMedicineId() {
        return medicineId;
    }

    public void setMedicineId(String medicineId) {
        this.medicineId = medicineId;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public void setMedicineName(String medicineName) {
        this.medicineName = medicineName;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }
}

