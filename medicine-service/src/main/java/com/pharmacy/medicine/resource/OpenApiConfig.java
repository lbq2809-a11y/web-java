package com.pharmacy.medicine.resource;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

/**
 * Cấu hình OpenAPI để hiển thị nút "Authorize" kiểu Bearer JWT trên giao diện Swagger UI.
 */
@OpenAPIDefinition(
        info = @Info(
                title = "Medicine Service API - Nhà Thuốc Trực Tuyến",
                version = "1.0.0",
                description = "Quản lý danh mục thuốc, tra cứu thuốc (OTC/ETC) và đánh giá sản phẩm"
        )
)
@SecurityScheme(
        securitySchemeName = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Dán JWT Access Token (không cần gõ chữ 'Bearer ' ở đầu)"
)
public class OpenApiConfig extends Application {
}

