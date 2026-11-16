package com.pharmacy.inventory.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * DTO nhận dữ liệu phân trang từ medicine-service (GET /api/v1/medicines).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MedicinePagedResponse {
    public List<MedicineResponse> items;
    public int page;
    public int size;
    public long totalItems;
    public int totalPages;
}
