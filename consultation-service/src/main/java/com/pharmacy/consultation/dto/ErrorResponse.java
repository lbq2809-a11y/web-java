package com.pharmacy.consultation.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Định dạng phản hồi lỗi chuẩn toàn hệ thống theo hợp đồng API.
 */
public class ErrorResponse {

    private String code;
    private String message;
    private List<String> details;

    public ErrorResponse() {
        this.details = new ArrayList<>();
    }

    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
        this.details = new ArrayList<>();
    }

    public ErrorResponse(String code, String message, List<String> details) {
        this.code = code;
        this.message = message;
        this.details = details != null ? details : new ArrayList<>();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getDetails() {
        return details;
    }

    public void setDetails(List<String> details) {
        this.details = details;
    }
}

