package com.pharmacy.prescription.exception;

import com.pharmacy.prescription.dto.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.List;

/**
 * Bộ xử lý lỗi toàn cục (ExceptionMapper) của prescription-service
 * đảm bảo mọi lỗi trả về đúng định dạng JSON:
 * {"code": "...", "message": "...", "details": [...]}
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof ApiException apiEx) {
            return Response.status(apiEx.getStatusCode())
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse(apiEx.getCode(), apiEx.getMessage(), apiEx.getDetails()))
                    .build();
        }

        if (exception instanceof ConstraintViolationException validationEx) {
            List<String> details = validationEx.getConstraintViolations()
                    .stream()
                    .map(ConstraintViolation::getMessage)
                    .toList();
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse("VALIDATION_ERROR", "Dữ liệu đầu vào không hợp lệ", details))
                    .build();
        }

        if (exception instanceof WebApplicationException webEx) {
            int status = webEx.getResponse() != null ? webEx.getResponse().getStatus() : 500;
            String code = switch (status) {
                case 400 -> "BAD_REQUEST";
                case 401 -> "UNAUTHORIZED";
                case 403 -> "FORBIDDEN";
                case 404 -> "NOT_FOUND";
                case 405 -> "METHOD_NOT_ALLOWED";
                default -> "HTTP_ERROR_" + status;
            };
            String message = webEx.getMessage() != null ? webEx.getMessage() : "Yêu cầu không hợp lệ";
            return Response.status(status)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse(code, message, Collections.emptyList()))
                    .build();
        }

        LOG.error("Lỗi hệ thống không xác định trong prescription-service", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("INTERNAL_SERVER_ERROR", "Đã xảy ra lỗi hệ thống, vui lòng thử lại sau", Collections.emptyList()))
                .build();
    }
}
