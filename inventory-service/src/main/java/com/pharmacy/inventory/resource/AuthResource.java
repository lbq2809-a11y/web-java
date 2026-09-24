package com.pharmacy.auth.resource;

import com.pharmacy.auth.dto.request.LoginRequest;
import com.pharmacy.auth.dto.request.RegisterRequest;
import com.pharmacy.auth.dto.response.AuthResponse;
import com.pharmacy.auth.exception.AuthException;
import com.pharmacy.auth.service.AuthService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @POST
    @Path("/register")
    public Response register(@Valid RegisterRequest request) {
        try {
            AuthResponse response = authService.register(request);
            return Response.status(Response.Status.CREATED).entity(response).build();
        } catch (AuthException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage())).build();
        }
    }

    @POST
    @Path("/login")
    public Response login(@Valid LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return Response.ok(response).build();
        } catch (AuthException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse(e.getMessage())).build();
        }
    }

    // Class lỗi đơn giản, để inline cho gọn
    public static class ErrorResponse {
        public String message;
        public ErrorResponse(String message) { this.message = message; }
    }
}