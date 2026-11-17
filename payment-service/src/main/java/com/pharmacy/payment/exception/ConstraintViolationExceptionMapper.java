package com.pharmacy.payment.exception;

import com.pharmacy.payment.dto.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

/**
 * Chuẩn hóa lỗi Bean Validation (400 VALIDATION_ERROR) về định dạng JSON thống nhất.
 */
@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<String> details = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .toList();

        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("VALIDATION_ERROR", "Dữ liệu yêu cầu không hợp lệ", details))
                .build();
    }
}
