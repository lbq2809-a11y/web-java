package com.pharmacy.recommendation.exception;

import com.pharmacy.recommendation.dto.ErrorResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.List;

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

        if (exception instanceof WebApplicationException webEx) {
            int status = webEx.getResponse() != null ? webEx.getResponse().getStatus() : 500;
            String code = switch (status) {
                case 400 -> "BAD_REQUEST";
                case 401 -> "UNAUTHORIZED";
                case 403 -> "FORBIDDEN";
                case 404 -> "NOT_FOUND";
                case 409 -> "CONFLICT";
                default -> "SERVER_ERROR";
            };
            String message = webEx.getMessage() != null ? webEx.getMessage() : "Yêu cầu không thể xử lý";
            return Response.status(status)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse(code, message, Collections.emptyList()))
                    .build();
        }

        LOG.errorf("Lỗi chưa được xử lý tại recommendation-service: %s", exception.getMessage(), exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("INTERNAL_SERVER_ERROR", "Đã xảy ra lỗi hệ thống nội bộ", List.of(exception.getMessage() != null ? exception.getMessage() : "Unknown error")))
                .build();
    }
}

