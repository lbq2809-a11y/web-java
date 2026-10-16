package com.pharmacy.medicine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO nhận dữ liệu tạo mới hoặc cập nhật danh mục thuốc.
 */
public class CategoryRequest {

    @NotBlank(message = "Mã danh mục không được để trống")
    @Size(max = 50, message = "Mã danh mục không vượt quá 50 ký tự")
    @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Mã danh mục chỉ gồm chữ in hoa, số, dấu gạch ngang hoặc gạch dưới")
    public String code;

    @NotBlank(message = "Tên danh mục không được để trống")
    @Size(max = 150, message = "Tên danh mục không vượt quá 150 ký tự")
    public String name;

    @Size(max = 500, message = "Mô tả không vượt quá 500 ký tự")
    public String description;

    public Boolean active = true;
}

