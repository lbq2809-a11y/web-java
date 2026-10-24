package com.pharmacy.consultation.dto;

/**
 * Kết quả nạp lại toàn bộ tri thức thuốc từ medicine-service.
 */
public class ReindexResponse {

    private String status;
    private String message;
    private int totalMedicines;
    private int totalChunks;

    public ReindexResponse() {
    }

    public ReindexResponse(String status, String message, int totalMedicines, int totalChunks) {
        this.status = status;
        this.message = message;
        this.totalMedicines = totalMedicines;
        this.totalChunks = totalChunks;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getTotalMedicines() {
        return totalMedicines;
    }

    public void setTotalMedicines(int totalMedicines) {
        this.totalMedicines = totalMedicines;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }
}

