package com.pharmacy.order.resource;

import com.pharmacy.order.dto.AddToCartRequest;
import com.pharmacy.order.dto.CartResponse;
import com.pharmacy.order.dto.UpdateCartItemRequest;
import com.pharmacy.order.exception.ApiException;
import com.pharmacy.order.service.CartService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
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
 * REST Resource quản lý giỏ hàng tạm thời của khách hàng lưu trữ trên Redis
 */
@Path("/api/v1/cart")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("KHACH_HANG")
@Tag(name = "Cart API", description = "Các API thao tác với giỏ hàng trên Redis của khách hàng")
@SecuritySchemes(value = {
        @SecurityScheme(
                securitySchemeName = "BearerAuth",
                type = SecuritySchemeType.HTTP,
                scheme = "bearer",
                bearerFormat = "JWT",
                description = "Dán JWT token vào đây (ví dụ lấy từ GET /api/v1/dev/tokens)"
        )
})
@SecurityRequirement(name = "BearerAuth")
public class CartResource {

    @Inject
    CartService cartService;

    @Inject
    JsonWebToken jwt;

    @GET
    @Operation(summary = "Xem chi tiết giỏ hàng hiện tại",
               description = "Lấy giỏ hàng từ Redis, đồng thời truy vấn giá niêm yết thời gian thực từ medicine-service.")
    @APIResponse(responseCode = "200", description = "Lấy giỏ hàng thành công")
    @APIResponse(responseCode = "401", description = "Chưa xác thực")
    public CartResponse getCart() {
        return cartService.getCartDetail(getUserId());
    }

    @POST
    @Path("/items")
    @Operation(summary = "Thêm thuốc vào giỏ hàng",
               description = "Thêm một loại thuốc với số lượng chỉ định vào giỏ hàng Redis (nếu đã có thì cộng dồn).")
    @APIResponse(responseCode = "200", description = "Thêm thuốc vào giỏ thành công")
    public CartResponse addItem(@Valid AddToCartRequest req) {
        UUID userId = getUserId();
        cartService.addItem(userId, req);
        return cartService.getCartDetail(userId);
    }

    @PUT
    @Path("/items/{medicineId}")
    @Operation(summary = "Cập nhật số lượng của thuốc trong giỏ hàng",
               description = "Cập nhật lại số lượng (nếu quantity <= 0 sẽ tự động xóa khỏi giỏ).")
    @APIResponse(responseCode = "200", description = "Cập nhật số lượng thành công")
    public CartResponse updateQuantity(
            @PathParam("medicineId") String medicineId,
            @Valid UpdateCartItemRequest req
    ) {
        UUID userId = getUserId();
        cartService.updateQuantity(userId, medicineId, req);
        return cartService.getCartDetail(userId);
    }

    @DELETE
    @Path("/items/{medicineId}")
    @Operation(summary = "Xóa một loại thuốc khỏi giỏ hàng")
    @APIResponse(responseCode = "204", description = "Xóa khỏi giỏ thành công")
    public Response removeItem(@PathParam("medicineId") String medicineId) {
        cartService.removeItem(getUserId(), medicineId);
        return Response.noContent().build();
    }

    @DELETE
    @Operation(summary = "Xóa toàn bộ giỏ hàng")
    @APIResponse(responseCode = "204", description = "Xóa sạch giỏ hàng thành công")
    public Response clearCart() {
        cartService.clearCart(getUserId());
        return Response.noContent().build();
    }

    private UUID getUserId() {
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw ApiException.badRequest("UNAUTHENTICATED", "Không xác định được danh tính người dùng từ token");
        }
        try {
            return UUID.fromString(sub.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_USER_ID", "ID người dùng trong token không đúng định dạng UUID");
        }
    }
}

