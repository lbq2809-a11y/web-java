package com.pharmacy.medicine.dto;

import java.util.List;

/**
 * Định dạng phản hồi lỗi thống nhất toàn hệ thống theo quy ước trong GEMINI.md:
 * {"code": "...", "message": "...", "details": [...]}
 */
public class ErrorResponse {
    public String code;
    public String message;
    public List<String> details;

    public ErrorResponse() {
    }

    public ErrorResponse(String code, String message, List<String> details) {
        this.code = code;
        this.message = message;
        this.details = details != null ? details : List.of();
    }
}

