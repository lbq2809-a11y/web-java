package com.pharmacy.medicine.exception;

import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Ngoại lệ nghiệp vụ chuẩn của medicine-service kèm mã lỗi và HTTP Status.
 */
public class ApiException extends RuntimeException {
    private final String code;
    private final Response.Status status;
    private final List<String> details;

    public ApiException(String code, String message, Response.Status status) {
        this(code, message, status, List.of());
    }

    public ApiException(String code, String message, Response.Status status, List<String> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details != null ? details : List.of();
    }

    public String getCode() {
        return code;
    }

    public Response.Status getStatus() {
        return status;
    }

    public List<String> getDetails() {
        return details;
    }
}

