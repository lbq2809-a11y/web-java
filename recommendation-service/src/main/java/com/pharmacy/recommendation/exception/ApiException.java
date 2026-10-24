package com.pharmacy.recommendation.exception;

import java.util.Collections;
import java.util.List;

public class ApiException extends RuntimeException {

    private final String code;
    private final int statusCode;
    private final List<String> details;

    public ApiException(String code, String message, int statusCode) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
        this.details = Collections.emptyList();
    }

    public ApiException(String code, String message, int statusCode, List<String> details) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
        this.details = details != null ? details : Collections.emptyList();
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(code, message, 400);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(code, message, 404);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException("UNAUTHORIZED", message, 401);
    }

    public static ApiException forbidden(String message) {
        return new ApiException("FORBIDDEN", message, 403);
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
}

