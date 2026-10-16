package com.pharmacy.medicine.dto;

import com.pharmacy.medicine.document.PrescriptionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO nhận dữ liệu tạo mới hoặc cập nhật thuốc kèm Bean Validation.
 */
public class MedicineRequest {

    @NotBlank(message = "Mã thuốc không được để trống")
    @Size(max = 50, message = "Mã thuốc không vượt quá 50 ký tự")
    @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Mã thuốc chỉ gồm chữ in hoa, số, dấu gạch ngang hoặc gạch dưới")
    public String code;

    @NotBlank(message = "Tên thuốc không được để trống")
    @Size(max = 255, message = "Tên thuốc không vượt quá 255 ký tự")
    public String name;

    @NotBlank(message = "Thành phần hoạt chất không được để trống")
    @Size(max = 500, message = "Thành phần hoạt chất không vượt quá 500 ký tự")
    public String activeIngredient;

    @NotBlank(message = "Nhà sản xuất không được để trống")
    @Size(max = 255, message = "Nhà sản xuất không vượt quá 255 ký tự")
    public String manufacturer;

    @NotBlank(message = "Đơn vị tính không được để trống")
    @Size(max = 50, message = "Đơn vị tính không vượt quá 50 ký tự")
    public String unit;

    @NotNull(message = "Giá bán không được để trống")
    @DecimalMin(value = "0.01", message = "Giá bán phải lớn hơn 0")
    public BigDecimal price;

    @Size(max = 500, message = "Đường dẫn ảnh không vượt quá 500 ký tự")
    public String imageUrl;

    @NotBlank(message = "Chỉ định không được để trống")
    @Size(max = 1000, message = "Chỉ định không vượt quá 1000 ký tự")
    public String indications;

    @NotBlank(message = "Chống chỉ định không được để trống")
    @Size(max = 1000, message = "Chống chỉ định không vượt quá 1000 ký tự")
    public String contraindications;

    @NotBlank(message = "Cách dùng không được để trống")
    @Size(max = 1000, message = "Cách dùng không vượt quá 1000 ký tự")
    public String dosage;

    @NotNull(message = "Loại thuốc (OTC/ETC) không được để trống")
    public PrescriptionType prescriptionType;

    @NotBlank(message = "Danh mục thuốc (categoryId) không được để trống")
    public String categoryId;

    public Boolean active = true;
}

