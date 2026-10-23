package com.pharmacy.inventory.resource;

import com.pharmacy.inventory.exception.ApiException;
import io.quarkus.arc.profile.IfBuildProfile;
import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
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
 * cho các vai trò (KHACH_HANG, DUOC_SI, ADMIN) để thử trên Swagger UI khi chưa chạy auth-service.
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
            @Parameter(description = "Email của người dùng (để trống sẽ dùng email mẫu)")
            @QueryParam("email") String email,
            @Parameter(description = "Họ và tên của người dùng (để trống sẽ dùng tên mẫu)")
            @QueryParam("name") String name
    ) {
        String roleUpper = role != null ? role.trim().toUpperCase() : "ADMIN";
        if (!ALLOWED_ROLES.contains(roleUpper)) {
            throw ApiException.badRequest("INVALID_ROLE",
                    "Vai trò '" + role + "' không hợp lệ. Chỉ chấp nhận: " + ALLOWED_ROLES);
        }

        String effectiveUserId = (userId != null && !userId.isBlank()) ? userId : switch (roleUpper) {
            case "ADMIN" -> "00000000-0000-0000-0000-000000000000";
            case "DUOC_SI" -> "22222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
            default -> "11111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        };

        String effectiveEmail = (email != null && !email.isBlank()) ? email : switch (roleUpper) {
            case "ADMIN" -> "admin@pharmacy.local";
            case "DUOC_SI" -> "duoc_si@pharmacy.local";
            default -> "khach_hang@pharmacy.local";
        };

        String effectiveName = (name != null && !name.isBlank()) ? name : switch (roleUpper) {
            case "ADMIN" -> "Quản Trị Viên Dev";
            case "DUOC_SI" -> "Dược Sĩ Dev";
            default -> "Khách Hàng Dev";
        };

        String token = Jwt.issuer(issuer)
                .subject(effectiveUserId)
                .upn(effectiveEmail)
                .claim("email", effectiveEmail)
                .claim("name", effectiveName)
                .groups(Set.of(roleUpper))
                .expiresIn(Duration.ofDays(7))
                .sign();

        return Map.of(
                "token", token,
                "tokenType", "Bearer",
                "role", roleUpper,
                "userId", effectiveUserId,
                "email", effectiveEmail,
                "name", effectiveName,
                "note", "Dán token này vào nút 'Authorize' (Bearer <token>) trên Swagger UI tại http://localhost:8087/q/swagger-ui"
        );
    }
}

