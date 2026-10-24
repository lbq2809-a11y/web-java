package com.pharmacy.consultation.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO đóng gói danh sách phân trang nhận từ medicine-service.
 */
public class PagedResponse<T> {

    private List<T> items = new ArrayList<>();
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;

    public PagedResponse() {
    }

    public PagedResponse(List<T> items, int page, int size, long totalItems, int totalPages) {
        this.items = items != null ? items : new ArrayList<>();
        this.page = page;
        this.size = size;
        this.totalItems = totalItems;
        this.totalPages = totalPages;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(long totalItems) {
        this.totalItems = totalItems;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}

