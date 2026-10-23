package com.pharmacy.gateway.dto;

import java.util.List;

/**
 * Định dạng phản hồi lỗi thống nhất toàn hệ sinh thái Pharmacy Microservices.
 */
public record ErrorResponse(
    String code,
    String message,
    List<String> details
) {
    public ErrorResponse(String code, String message) {
        this(code, message, List.of());
    }
}

