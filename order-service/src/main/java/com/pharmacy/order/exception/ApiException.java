package com.pharmacy.order.exception;

import jakarta.ws.rs.core.Response;

import java.util.Collections;
import java.util.List;

/**
 * Ngoại lệ nghiệp vụ chuẩn của order-service kèm mã lỗi và HTTP Status.
 */
public class ApiException extends RuntimeException {

    private final String code;
    private final int statusCode;
    private final List<String> details;

    public ApiException(String code, String message, Response.Status status) {
        this(code, message, status != null ? status.getStatusCode() : 500, Collections.emptyList());
    }

    public ApiException(String code, String message, Response.Status status, List<String> details) {
        this(code, message, status != null ? status.getStatusCode() : 500, details);
    }

    public ApiException(String code, String message, int statusCode) {
        this(code, message, statusCode, Collections.emptyList());
    }

    public ApiException(String code, String message, int statusCode, List<String> details) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
        this.details = details != null ? details : Collections.emptyList();
    }

    public String getCode() {
        return code;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public List<String> getDetails() {
        return details;
    }

    // Factory methods tiện ích
    public static ApiException notFound(String message) {
        return new ApiException("ORDER_NOT_FOUND", message, Response.Status.NOT_FOUND);
    }

    public static ApiException forbidden(String message) {
        return new ApiException("FORBIDDEN", message, Response.Status.FORBIDDEN);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(code, message, Response.Status.BAD_REQUEST);
    }

    public static ApiException badRequest(String code, String message, List<String> details) {
        return new ApiException(code, message, Response.Status.BAD_REQUEST, details);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(code, message, Response.Status.CONFLICT);
    }

    public static ApiException unprocessable(String code, String message, List<String> details) {
        return new ApiException(code, message, 422, details);
    }
}
