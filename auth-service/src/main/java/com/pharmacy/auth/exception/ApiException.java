package com.pharmacy.auth.exception;

import jakarta.ws.rs.core.Response;

import java.util.Collections;
import java.util.List;

public class ApiException extends RuntimeException {

    private final String code;
    private final List<String> details;
    private final Response.Status status;

    public ApiException(String code, String message, Response.Status status, List<String> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details != null ? details : Collections.emptyList();
    }

    public ApiException(String code, String message, Response.Status status) {
        this(code, message, status, Collections.emptyList());
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(code, message, Response.Status.BAD_REQUEST);
    }

    public static ApiException badRequest(String code, String message, List<String> details) {
        return new ApiException(code, message, Response.Status.BAD_REQUEST, details);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(code, message, Response.Status.UNAUTHORIZED);
    }

    public static ApiException forbidden(String message) {
        return new ApiException("FORBIDDEN", message, Response.Status.FORBIDDEN);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(code, message, Response.Status.FORBIDDEN);
    }

    public static ApiException notFound(String message) {
        return new ApiException("NOT_FOUND", message, Response.Status.NOT_FOUND);
    }

    public String getCode() {
        return code;
    }

    public List<String> getDetails() {
        return details;
    }

    public Response.Status getStatus() {
        return status;
    }
}

