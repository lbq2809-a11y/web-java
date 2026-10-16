package com.pharmacy.medicine.dto;

import java.util.List;

/**
 * DTO đóng gói dữ liệu danh sách có phân trang thống nhất.
 */
public class PagedResponse<T> {
    public List<T> items;
    public int page;
    public int size;
    public long totalItems;
    public int totalPages;

    public PagedResponse() {
    }

    public PagedResponse(List<T> items, int page, int size, long totalItems, int totalPages) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.totalItems = totalItems;
        this.totalPages = totalPages;
    }
}

