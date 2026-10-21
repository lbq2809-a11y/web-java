package com.pharmacy.prescription.resource;

import com.pharmacy.prescription.dto.LinkOrderRequest;
import com.pharmacy.prescription.dto.LinkOrderResponse;
import com.pharmacy.prescription.dto.ValidatePrescriptionRequest;
import com.pharmacy.prescription.dto.ValidatePrescriptionResponse;
import com.pharmacy.prescription.service.PrescriptionService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/**
 * REST API nội bộ phục vụ order-service giao tiếp khi tạo đơn hàng có thuốc kê đơn (ETC)
 * Tuân thủ mục 2.2 của docs/API-CONTRACT.md
 */
@Path("/api/v1/prescriptions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Internal Prescription API", description = "Các API nội bộ cho order-service kiểm tra tính hợp lệ và liên kết đơn thuốc")
@SecurityRequirement(name = "BearerAuth")
public class InternalPrescriptionResource {

    @Inject
    PrescriptionService prescriptionService;

    @POST
    @Path("/{id}/validate-for-order")
    @RolesAllowed({"KHACH_HANG", "DUOC_SI", "ADMIN"})
    @Operation(summary = "Kiểm tra tính hợp lệ của đơn thuốc cho các thuốc kê đơn (ETC) khi đặt hàng",
               description = "Kiểm tra đơn tồn tại, đúng chủ đơn, trạng thái DA_DUYET, chưa hết hạn, và số lượng đặt không vượt quá maxAllowedQuantity.")
    @APIResponse(responseCode = "200", description = "Đơn thuốc hợp lệ")
    @APIResponse(responseCode = "403", description = "Đơn thuốc không thuộc về khách hàng đang đặt")
    @APIResponse(responseCode = "404", description = "Không tìm thấy đơn thuốc")
    @APIResponse(responseCode = "422", description = "Đơn thuốc hết hạn, chưa duyệt hoặc sai danh mục/số lượng thuốc")
    public ValidatePrescriptionResponse validateForOrder(
            @PathParam("id") UUID id,
            @Valid ValidatePrescriptionRequest request
    ) {
        return prescriptionService.validateForOrder(id, request);
    }

    @POST
    @Path("/{id}/order-links")
    @RolesAllowed({"KHACH_HANG", "DUOC_SI", "ADMIN"})
    @Operation(summary = "Gắn đơn thuốc vào đơn hàng (Idempotent)",
               description = "Gắn đơn thuốc vào orderId sau khi order-service tạo đơn hàng thành công.")
    @APIResponse(responseCode = "201", description = "Gắn đơn thuốc vào đơn hàng thành công")
    @APIResponse(responseCode = "200", description = "Đơn hàng đã được gắn trước đó (Idempotent)")
    @APIResponse(responseCode = "409", description = "Đơn thuốc đã dùng cho đơn hàng khác")
    public Response linkOrder(
            @PathParam("id") UUID id,
            @Valid LinkOrderRequest request
    ) {
        LinkOrderResponse response = prescriptionService.linkOrder(id, request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }
}

