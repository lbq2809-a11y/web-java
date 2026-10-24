package com.pharmacy.consultation.exception;

import com.pharmacy.consultation.dto.ErrorResponse;
import jakarta.annotation.Priority;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

/**
 * Xử lý lỗi xác thực Bean Validation (@Valid, @Size, @NotBlank), đảm bảo trả về HTTP 400
 * với cấu trúc JSON chuẩn: {"code": "VALIDATION_ERROR", "message": "...", "details": [...]}.
 */
@Provider
@Priority(Priorities.USER)
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<String> details = exception.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessage)
                .toList();

        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("VALIDATION_ERROR", "Dữ liệu đầu vào không hợp lệ", details))
                .build();
    }
}

