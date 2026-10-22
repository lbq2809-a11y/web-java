package com.pharmacy.auth.exception;

import com.pharmacy.auth.dto.response.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<String> details = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .toList();

        ErrorResponse errorResponse = new ErrorResponse(
                "VALIDATION_ERROR",
                "Dữ liệu yêu cầu không hợp lệ",
                details
        );

        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(errorResponse)
                .build();
    }
}

