package com.pharmacy.auth.exception;

import com.pharmacy.auth.dto.response.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {

    @Override
    public Response toResponse(ApiException exception) {
        ErrorResponse errorResponse = new ErrorResponse(
                exception.getCode(),
                exception.getMessage(),
                exception.getDetails()
        );
        return Response.status(exception.getStatus())
                .type(MediaType.APPLICATION_JSON)
                .entity(errorResponse)
                .build();
    }
}

