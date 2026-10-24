package com.pharmacy.consultation.resource;

import com.pharmacy.consultation.exception.ApiException;
import io.quarkus.arc.profile.IfBuildProfile;
import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Endpoint tiện ích CHỈ BẬT Ở PROFILE DEV & TEST (@IfBuildProfile) giúp sinh nhanh JWT token thử nghiệm
 * theo đúng hợp đồng docs/JWT-CONTRACT.md cho 3 vai trò (KHACH_HANG, DUOC_SI, ADMIN) để thử trên Swagger UI
 * khi chưa chạy auth-service thật.
 */
@Path("/api/v1/dev/tokens")
@Produces(MediaType.APPLICATION_JSON)
@IfBuildProfile(anyOf = {"dev", "test"})
@Tag(name = "Dev Token Generator (Dev/Test Only)", description = "Sinh JWT token thử nghiệm cho KHACH_HANG, DUOC_SI, ADMIN để dùng với nút Authorize trên Swagger UI")
public class DevTokenResource {

    private static final Set<String> ALLOWED_ROLES = Set.of("KHACH_HANG", "DUOC_SI", "ADMIN");

    @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "https://pharmacy.com/issuer")
    String issuer;

    @GET
    @PermitAll
    @Operation(
            summary = "Sinh JWT token thử nghiệm cho vai trò được chọn (Chỉ hoạt động ở chế độ dev/test)",
            description = "Chọn role = KHACH_HANG, DUOC_SI hoặc ADMIN. Copy giá trị 'token' trả về và dán vào nút Authorize ở góc trên bên phải Swagger UI."
    )
    public Map<String, Object> generateTestToken(
            @Parameter(description = "Vai trò cần tạo token: KHACH_HANG, DUOC_SI hoặc ADMIN")
            @QueryParam("role") @DefaultValue("ADMIN") String role,
            @Parameter(description = "UUID của người dùng (để trống sẽ dùng UUID mẫu cố định theo từng role)")
            @QueryParam("userId") String userId,
            @Parameter(description = "Email người dùng")
            @QueryParam("email") String email,
            @Parameter(description = "Họ tên người dùng")
            @QueryParam("name") String name
    ) {
        String normalizedRole = role != null ? role.trim().toUpperCase() : "ADMIN";
        if (!ALLOWED_ROLES.contains(normalizedRole)) {
            throw new ApiException(
                    "INVALID_ROLE",
                    "Vai trò chỉ được nhận một trong 3 giá trị: KHACH_HANG, DUOC_SI, ADMIN",
                    Response.Status.BAD_REQUEST
            );
        }

        String actualUserId = (userId != null && !userId.isBlank()) ? userId : switch (normalizedRole) {
            case "ADMIN" -> "00000000-0000-0000-0000-000000000001";
            case "DUOC_SI" -> "00000000-0000-0000-0000-000000000002";
            default -> "00000000-0000-0000-0000-000000000003";
        };

        String actualEmail = (email != null && !email.isBlank()) ? email : switch (normalizedRole) {
            case "ADMIN" -> "admin@pharmacy.local";
            case "DUOC_SI" -> "duocsi@pharmacy.local";
            default -> "khachhang@pharmacy.local";
        };

        String actualName = (name != null && !name.isBlank()) ? name : switch (normalizedRole) {
            case "ADMIN" -> "Quản Trị Viên";
            case "DUOC_SI" -> "Dược Sĩ Trực Ban";
            default -> "Khách Hàng Mẫu";
        };

        String token = Jwt.issuer(issuer)
                .subject(actualUserId)
                .upn(actualEmail)
                .claim("email", actualEmail)
                .claim("name", actualName)
                .claim("phone", "0901234567")
                .groups(Set.of(normalizedRole))
                .expiresIn(Duration.ofDays(1))
                .sign();

        return Map.of(
                "token", token,
                "tokenType", "Bearer",
                "expiresIn", 86400,
                "role", normalizedRole,
                "userId", actualUserId,
                "email", actualEmail,
                "name", actualName
        );
    }
}

