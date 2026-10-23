package com.pharmacy.auth.resource;

import com.pharmacy.auth.dto.request.ConfirmQrRequest;
import com.pharmacy.auth.dto.request.LoginRequest;
import com.pharmacy.auth.dto.request.RegisterRequest;
import com.pharmacy.auth.dto.response.AuthResponse;
import com.pharmacy.auth.dto.response.ConfirmQrResponse;
import com.pharmacy.auth.dto.response.CreateQrSessionResponse;
import com.pharmacy.auth.dto.response.QrSessionStatusResponse;
import com.pharmacy.auth.dto.response.UserResponse;
import com.pharmacy.auth.exception.ApiException;
import com.pharmacy.auth.service.AuthService;
import com.pharmacy.auth.service.QrSessionService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/**
 * REST Resource cung cấp các API Xác thực: Đăng ký, Đăng nhập, Hồ sơ người dùng và Đăng nhập QR Code
 */
@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Auth API", description = "Các API xác thực: Đăng ký, Đăng nhập, Hồ sơ và Đăng nhập QR Code")
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

    @Inject
    QrSessionService qrSessionService;

    @Inject
    JsonWebToken jwt;

    @Context
    HttpServerRequest httpRequest;

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

    // =========================================================================================
    // GIAI ĐOẠN 3: ĐĂNG NHẬP QR CODE
    // =========================================================================================

    @POST
    @Path("/qr/session")
    @PermitAll
    @Consumes(MediaType.WILDCARD)
    @Operation(summary = "Tạo phiên đăng nhập QR mới (Công khai)",
               description = "Tạo phiên đăng nhập QR với sessionId ngẫu nhiên, mã bí mật pollSecret, giới hạn 10 phiên/phút theo IP.")
    @APIResponse(responseCode = "201", description = "Tạo phiên QR thành công")
    @APIResponse(responseCode = "429", description = "Vượt quá giới hạn tạo phiên theo IP")
    public Response createQrSession(@HeaderParam("X-Forwarded-For") String xForwardedFor) {
        String clientIp = resolveClientIp(xForwardedFor);
        CreateQrSessionResponse session = qrSessionService.createSession(clientIp);
        return Response.status(Response.Status.CREATED).entity(session).build();
    }

    @GET
    @Path("/qr/session/{sessionId}")
    @PermitAll
    @Operation(summary = "Kiểm tra trạng thái phiên QR (Công khai - Bắt buộc X-QR-Secret)",
               description = "Trình duyệt kiểm tra trạng thái phiên QR. Bắt buộc có header X-QR-Secret khớp pollSecret. " +
                             "Khi CONFIRMED, trả về token và XÓA phiên khỏi Redis ngay lập tức (dùng 1 lần).")
    @APIResponse(responseCode = "200", description = "Trả về trạng thái phiên (PENDING hoặc CONFIRMED kèm token)")
    @APIResponse(responseCode = "403", description = "Thiếu hoặc sai header X-QR-Secret")
    @APIResponse(responseCode = "404", description = "Phiên QR không tồn tại hoặc đã hết hạn")
    public QrSessionStatusResponse getQrSessionStatus(
            @PathParam("sessionId") String sessionId,
            @HeaderParam("X-QR-Secret") String clientSecret) {
        return qrSessionService.getSessionStatus(sessionId, clientSecret);
    }

    @POST
    @Path("/qr/confirm")
    @RolesAllowed({"KHACH_HANG", "DUOC_SI", "ADMIN"})
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Thiết bị di động xác nhận phiên đăng nhập QR (Bắt buộc JWT)",
               description = "Thiết bị di động đã đăng nhập quét mã QR và xác nhận. Định danh userId được trích xuất từ JWT token (claim sub).")
    @APIResponse(responseCode = "200", description = "Xác nhận phiên QR thành công")
    @APIResponse(responseCode = "400", description = "Dữ liệu không hợp lệ")
    @APIResponse(responseCode = "401", description = "Thiếu hoặc không hợp lệ JWT token")
    @APIResponse(responseCode = "404", description = "Phiên QR không tồn tại hoặc đã hết hạn")
    @APIResponse(responseCode = "409", description = "Phiên QR đã được xác nhận trước đó")
    public ConfirmQrResponse confirmQrSession(@Valid ConfirmQrRequest request) {
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw ApiException.unauthorized("UNAUTHORIZED", "Không xác định được danh tính từ token");
        }
        UUID userId;
        try {
            userId = UUID.fromString(sub.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_USER_ID", "ID người dùng không đúng định dạng UUID");
        }

        return qrSessionService.confirmSession(request.sessionId(), userId);
    }

    private String resolveClientIp(String xForwardedFor) {
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String firstIp = xForwardedFor.split(",")[0].trim();
            if (!firstIp.isEmpty()) {
                return firstIp;
            }
        }
        if (httpRequest != null && httpRequest.remoteAddress() != null && httpRequest.remoteAddress().host() != null) {
            return httpRequest.remoteAddress().host();
        }
        return "127.0.0.1";
    }
}
