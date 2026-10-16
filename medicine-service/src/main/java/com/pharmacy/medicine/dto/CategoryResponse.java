package com.pharmacy.medicine.dto;

import java.time.Instant;

/**
 * DTO trả thông tin danh mục thuốc về cho client (không trả trực tiếp Entity/Document).
 */
public class CategoryResponse {
    public String id;
    public String code;
    public String name;
    public String description;
    public boolean active;
    public Instant createdAt;
    public Instant updatedAt;
}

