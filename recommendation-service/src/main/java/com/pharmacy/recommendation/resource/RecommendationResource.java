package com.pharmacy.recommendation.resource;

import com.pharmacy.recommendation.dto.RecommendationResponse;
import com.pharmacy.recommendation.exception.ApiException;
import com.pharmacy.recommendation.service.RecommendationService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/v1/recommendations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Recommendations", description = "Hệ thống gợi ý thuốc thông minh theo thống kê mua hàng và an toàn dược phẩm")
@SecuritySchemes(value = {
        @SecurityScheme(
                securitySchemeName = "jwt",
                type = SecuritySchemeType.HTTP,
                scheme = "bearer",
                bearerFormat = "JWT"
        )
})
public class RecommendationResource {

    @Inject
    RecommendationService recommendationService;

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/also-bought/{medicineId}")
    @PermitAll
    @Operation(
            summary = "Gợi ý thuốc thường được mua kèm với một loại thuốc cụ thể (Frequently Bought Together)",
            description = "Dựa trên ma trận đồng xuất hiện giữa các đơn hàng hoàn thành. Thuốc kê đơn (ETC) vẫn được trả về với cờ requiresPrescription=true."
    )
    public List<RecommendationResponse> getAlsoBought(
            @Parameter(description = "Mã thuốc gốc (ObjectId 24 ký tự hex)", required = true)
            @PathParam("medicineId") String medicineId,
            @Parameter(description = "Số lượng thuốc gợi ý tối đa (1 đến 20, mặc định: 5)")
            @QueryParam("limit") @DefaultValue("5") int limit
    ) {
        return recommendationService.getAlsoBought(medicineId, limit);
    }

    @GET
    @Path("/for-me")
    @RolesAllowed({"KHACH_HANG", "DUOC_SI", "ADMIN"})
    @SecurityRequirement(name = "jwt")
    @Operation(
            summary = "Gợi ý thuốc cá nhân hóa cho người dùng đang đăng nhập (Personalized For Me)",
            description = "Dựa trên lịch sử mua hàng của người dùng, loại trừ các thuốc đã mua. NGUYÊN TẮC AN TOÀN: TUYỆT ĐỐI KHÔNG GỢI Ý THUỐC ETC. Tự động fallback sang Top bán chạy (Best Sellers) nếu là khách mới."
    )
    public List<RecommendationResponse> getForMe(
            @Parameter(description = "Số lượng thuốc gợi ý tối đa (1 đến 20, mặc định: 5)")
            @QueryParam("limit") @DefaultValue("5") int limit
    ) {
        String userId = jwt.getSubject();
        if (userId == null || userId.isBlank()) {
            throw ApiException.unauthorized("Không tìm thấy định danh người dùng trong JWT token");
        }
        return recommendationService.getForMe(userId, limit);
    }

    @GET
    @Path("/related/{medicineId}")
    @PermitAll
    @Operation(
            summary = "Gợi ý thuốc cùng danh mục điều trị (Related by Category)",
            description = "Tra cứu categoryId của thuốc truyền vào từ medicine-service và trả về các thuốc khác trong cùng danh mục."
    )
    public List<RecommendationResponse> getRelated(
            @Parameter(description = "Mã thuốc gốc (ObjectId 24 ký tự hex)", required = true)
            @PathParam("medicineId") String medicineId,
            @Parameter(description = "Số lượng thuốc gợi ý tối đa (1 đến 20, mặc định: 5)")
            @QueryParam("limit") @DefaultValue("5") int limit
    ) {
        return recommendationService.getRelated(medicineId, limit);
    }
}

