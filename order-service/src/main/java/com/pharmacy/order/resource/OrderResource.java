package com.pharmacy.order.resource;

import com.pharmacy.order.dto.CancelOrderRequest;
import com.pharmacy.order.dto.CheckoutRequest;
import com.pharmacy.order.dto.OrderPageResponse;
import com.pharmacy.order.dto.OrderResponse;
import com.pharmacy.order.dto.OrderStatusHistoryResponse;
import com.pharmacy.order.dto.RefundOrderRequest;
import com.pharmacy.order.dto.UpdateOrderStatusRequest;
import com.pharmacy.order.entity.OrderStatus;
import com.pharmacy.order.service.OrderService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

/**
 * REST Resource quản lý đơn hàng: Đặt hàng (Checkout), Tra cứu lịch sử và Chi tiết đơn hàng
 */
@Path("/api/v1/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Order API", description = "Các API đặt hàng (Checkout) và quản lý đơn hàng")
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
public class OrderResource {

    @Inject
    OrderService orderService;

    @POST
    @Path("/checkout")
    @RolesAllowed("KHACH_HANG")
    @Operation(summary = "Khách hàng tiến hành đặt hàng (Checkout) từ giỏ hàng hiện tại",
               description = "Kiểm tra điều kiện thuốc kê đơn (ETC) với prescription-service, lưu SNAPSHOT thông tin thuốc, xóa giỏ hàng và phát event order.created. Hỗ trợ Header 'Idempotency-Key' chống đặt trùng.")
    @APIResponse(responseCode = "201", description = "Đặt hàng thành công")
    @APIResponse(responseCode = "200", description = "Đơn hàng đã được đặt trước đó với Idempotency-Key này (Idempotent)")
    @APIResponse(responseCode = "400", description = "Giỏ hàng rỗng hoặc thiếu prescriptionId cho thuốc ETC")
    @APIResponse(responseCode = "422", description = "Đơn thuốc không hợp lệ hoặc đã hết hạn")
    public Response checkout(
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            @Valid CheckoutRequest request
    ) {
        OrderResponse response = orderService.checkout(idempotencyKey, request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @GET
    @Path("/my-orders")
    @RolesAllowed("KHACH_HANG")
    @Operation(summary = "Khách hàng xem danh sách đơn hàng của chính mình (phân trang)",
               description = "Hỗ trợ lọc theo trạng thái (CHO_XU_LY, DANG_GIAO, HOAN_THANH, DA_HUY, HOAN_TRA).")
    public OrderPageResponse getMyOrders(
            @QueryParam("status") OrderStatus status,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size
    ) {
        return orderService.getMyOrders(status, page, size);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"KHACH_HANG", "DUOC_SI", "ADMIN"})
    @Operation(summary = "Xem chi tiết một đơn hàng kèm SNAPSHOT các mặt hàng và địa chỉ giao hàng",
               description = "Khách hàng chỉ xem được đơn của mình. Dược sĩ và Admin xem được mọi đơn. Phục vụ payment-service lấy số tiền đối chiếu thanh toán.")
    @APIResponse(responseCode = "200", description = "Lấy chi tiết đơn hàng thành công")
    @APIResponse(responseCode = "403", description = "Không có quyền xem đơn hàng của người khác")
    @APIResponse(responseCode = "404", description = "Không tìm thấy đơn hàng")
    public OrderResponse getOrderById(@PathParam("id") UUID id) {
        return orderService.getOrderById(id);
    }

    @PUT
    @Path("/{id}/status")
    @RolesAllowed({"DUOC_SI", "ADMIN"})
    @Operation(summary = "Dược sĩ hoặc Admin cập nhật trạng thái đơn hàng",
               description = "Chuyển trạng thái theo máy trạng thái hợp lệ: CHO_XU_LY -> DANG_GIAO -> HOAN_THANH. Chặn các bước nhảy sai quy tắc. Phát sự kiện order.status-changed.")
    @APIResponse(responseCode = "200", description = "Cập nhật trạng thái thành công")
    @APIResponse(responseCode = "400", description = "Chuyển trạng thái sai quy tắc (INVALID_STATUS_TRANSITION)")
    @APIResponse(responseCode = "404", description = "Không tìm thấy đơn hàng")
    public OrderResponse updateOrderStatus(
            @PathParam("id") UUID id,
            @Valid UpdateOrderStatusRequest request
    ) {
        return orderService.updateOrderStatus(id, request);
    }

    @POST
    @Path("/{id}/cancel")
    @RolesAllowed({"KHACH_HANG", "ADMIN"})
    @Operation(summary = "Khách hàng hủy đơn hàng của chính mình (chỉ khi đang CHO_XU_LY)",
               description = "Chỉ cho phép hủy khi đơn ở trạng thái CHO_XU_LY. Phát sự kiện order.status-changed.")
    @APIResponse(responseCode = "200", description = "Hủy đơn hàng thành công")
    @APIResponse(responseCode = "400", description = "Đơn không ở trạng thái CHO_XU_LY")
    @APIResponse(responseCode = "403", description = "Không có quyền hủy đơn hàng của người khác")
    @APIResponse(responseCode = "404", description = "Không tìm thấy đơn hàng")
    public OrderResponse cancelOrder(
            @PathParam("id") UUID id,
            @Valid CancelOrderRequest request
    ) {
        return orderService.cancelOrder(id, request);
    }

    @POST
    @Path("/{id}/refund")
    @RolesAllowed({"KHACH_HANG", "ADMIN"})
    @Operation(summary = "Khách hàng yêu cầu hoàn trả đơn hàng (chỉ khi đã HOAN_THANH)",
               description = "Chỉ cho phép hoàn trả khi đơn ở trạng thái HOAN_THANH. Phát sự kiện order.status-changed.")
    @APIResponse(responseCode = "200", description = "Yêu cầu hoàn trả thành công")
    @APIResponse(responseCode = "400", description = "Đơn không ở trạng thái HOAN_THANH")
    @APIResponse(responseCode = "403", description = "Không có quyền yêu cầu hoàn trả đơn hàng của người khác")
    @APIResponse(responseCode = "404", description = "Không tìm thấy đơn hàng")
    public OrderResponse refundOrder(
            @PathParam("id") UUID id,
            @Valid RefundOrderRequest request
    ) {
        return orderService.refundOrder(id, request);
    }

    @GET
    @Path("/{id}/history")
    @RolesAllowed({"KHACH_HANG", "DUOC_SI", "ADMIN"})
    @Operation(summary = "Xem lịch sử thay đổi trạng thái của đơn hàng (Audit Trail)",
               description = "Khách hàng chỉ xem được lịch sử đơn của mình. Dược sĩ và Admin xem được mọi đơn.")
    @APIResponse(responseCode = "200", description = "Lấy lịch sử trạng thái thành công")
    @APIResponse(responseCode = "403", description = "Không có quyền xem lịch sử đơn hàng của người khác")
    @APIResponse(responseCode = "404", description = "Không tìm thấy đơn hàng")
    public List<OrderStatusHistoryResponse> getOrderHistory(@PathParam("id") UUID id) {
        return orderService.getOrderHistory(id);
    }
}

