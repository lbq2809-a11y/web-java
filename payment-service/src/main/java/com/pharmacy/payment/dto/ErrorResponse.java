package com.pharmacy.payment.dto;

import java.util.List;

/**
 * Định dạng lỗi JSON thống nhất theo quy ước hệ thống.
 */
public record ErrorResponse(
        String code,
        String message,
        List<String> details
) {
}
