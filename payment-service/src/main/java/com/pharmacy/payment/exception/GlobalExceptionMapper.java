package com.pharmacy.payment.exception;

import com.pharmacy.payment.dto.ErrorResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Chuẩn hóa toàn bộ lỗi REST trong payment-service sang cấu trúc ErrorResponse JSON thống nhất.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException webEx) {
            Response raw = webEx.getResponse();
            int status = raw != null ? raw.getStatus() : 500;
            Object entity = raw != null ? raw.getEntity() : null;

            if (entity instanceof ErrorResponse errorResponse) {
                return Response.status(status)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(errorResponse)
                        .build();
            }

            String message = (entity instanceof String str && !str.isBlank())
                    ? str
                    : (webEx.getMessage() != null ? webEx.getMessage() : "Yêu cầu không hợp lệ");

            return Response.status(status)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse(codeForStatus(status), message, List.of()))
                    .build();
        }

        LOG.error("Lỗi hệ thống không mong đợi trong payment-service", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("INTERNAL_SERVER_ERROR", "Đã xảy ra lỗi hệ thống nội bộ", List.of()))
                .build();
    }

    public static String codeForStatus(int status) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "PAYMENT_NOT_FOUND";
            case 409 -> "PAYMENT_CONFLICT";
            case 501 -> "NOT_IMPLEMENTED";
            case 502 -> "BAD_GATEWAY";
            default -> "PAYMENT_ERROR";
        };
    }
}
