package com.pharmacy.auth.resource;

import com.pharmacy.auth.dto.request.LoginRequest;
import com.pharmacy.auth.dto.request.RegisterRequest;
import com.pharmacy.auth.dto.response.AuthResponse;
import com.pharmacy.auth.dto.response.UserResponse;
import com.pharmacy.auth.service.AuthService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST Resource cung cấp các API Xác thực: Đăng ký, Đăng nhập, và Hồ sơ người dùng
 */
@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Auth API", description = "Các API xác thực: Đăng ký, Đăng nhập và Lấy thông tin tài khoản")
@SecuritySchemes(value = {
        @SecurityScheme(
                securitySchemeName = "BearerAuth",
                type = SecuritySchemeType.HTTP,
                scheme = "bearer",
                bearerFormat = "JWT",
                description = "Dán JWT token vào đây"
        )
})
public class AuthResource {

    @Inject
    AuthService authService;

    @POST
    @Path("/register")
    @PermitAll
    @Operation(summary = "Đăng ký tài khoản khách hàng mới",
               description = "Tạo mới tài khoản khách hàng, băm mật khẩu BCrypt, tự sinh UUID và trả về access token.")
    @APIResponse(responseCode = "201", description = "Đăng ký tài khoản thành công")
    @APIResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc email đã tồn tại")
    public Response register(@Valid RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @POST
    @Path("/login")
    @PermitAll
    @Operation(summary = "Đăng nhập hệ thống",
               description = "Xác thực bằng email và mật khẩu, cấp phát JSON Web Token (JWT) có thời hạn 24 giờ.")
    @APIResponse(responseCode = "200", description = "Đăng nhập thành công, trả về access token")
    @APIResponse(responseCode = "401", description = "Email hoặc mật khẩu không chính xác")
    @APIResponse(responseCode = "403", description = "Tài khoản đang bị khóa")
    public Response login(@Valid LoginRequest request) {
        AuthResponse response = authService.login(request);
        return Response.ok(response).build();
    }

    @GET
    @Path("/me")
    @RolesAllowed({"KHACH_HANG", "DUOC_SI", "ADMIN"})
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Lấy thông tin tài khoản hiện tại",
               description = "Đọc định danh user từ JWT token (Bearer) và trả về thông tin hồ sơ người dùng.")
    @APIResponse(responseCode = "200", description = "Lấy thông tin tài khoản thành công")
    @APIResponse(responseCode = "401", description = "Chưa xác thực hoặc token không hợp lệ")
    @APIResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    public UserResponse getMe() {
        return authService.getMe();
    }
}

