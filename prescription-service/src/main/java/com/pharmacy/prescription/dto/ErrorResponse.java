package com.pharmacy.prescription.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;

/**
 * Định dạng phản hồi lỗi thống nhất toàn hệ thống:
 * {"code": "...", "message": "...", "details": [...]}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        String code,
        String message,
        List<String> details
) {
    public ErrorResponse(String code, String message) {
        this(code, message, Collections.emptyList());
    }
}

