package com.pharmacy.recommendation.resource;

import com.pharmacy.recommendation.exception.ApiException;
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
 * cho các vai trò (KHACH_HANG, DUOC_SI, ADMIN) để dùng thử trên Swagger UI.
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
            @Parameter(description = "Vai trò người dùng: KHACH_HANG, DUOC_SI hoặc ADMIN", example = "KHACH_HANG")
            @QueryParam("role") @DefaultValue("KHACH_HANG") String role,
            @Parameter(description = "User ID tùy chọn (mặc định tự sinh ngẫu nhiên)")
            @QueryParam("userId") String userId
    ) {
        String normalizedRole = role != null ? role.trim().toUpperCase() : "KHACH_HANG";
        if (!ALLOWED_ROLES.contains(normalizedRole)) {
            throw ApiException.badRequest("INVALID_ROLE", "Vai trò không hợp lệ: " + role + ". Chỉ chấp nhận: " + ALLOWED_ROLES);
        }

        String effectiveUserId = (userId != null && !userId.isBlank()) ? userId.trim() : UUID.randomUUID().toString();
        String email = normalizedRole.toLowerCase() + "@pharmacy.local";
        String fullName = switch (normalizedRole) {
            case "ADMIN" -> "Quản Trị Viên Test";
            case "DUOC_SI" -> "Dược Sĩ Test";
            default -> "Khách Hàng Test";
        };

        String token = Jwt.issuer(issuer)
                .subject(effectiveUserId)
                .upn(email)
                .groups(Set.of(normalizedRole))
                .claim("email", email)
                .claim("name", fullName)
                .claim("role", normalizedRole)
                .expiresIn(Duration.ofDays(7))
                .sign();

        return Map.of(
                "token", token,
                "tokenType", "Bearer",
                "role", normalizedRole,
                "userId", effectiveUserId,
                "email", email,
                "name", fullName,
                "expiresInDays", 7,
                "note", "Copy chuỗi token và dán vào ô 'Value' trong popup Authorize trên Swagger UI (có hoặc không có tiền tố Bearer đều được)."
        );
    }
}

