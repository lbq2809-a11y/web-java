package com.pharmacy.prescription.resource;

import com.pharmacy.prescription.dto.ApprovePrescriptionRequest;
import com.pharmacy.prescription.dto.PrescriptionPageResponse;
import com.pharmacy.prescription.dto.PrescriptionResponse;
import com.pharmacy.prescription.dto.PrescriptionUploadForm;
import com.pharmacy.prescription.dto.RejectPrescriptionRequest;
import com.pharmacy.prescription.entity.PrescriptionStatus;
import com.pharmacy.prescription.service.PrescriptionService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
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
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/**
 * REST Resource chính quản lý đơn thuốc: upload ảnh, xem danh sách, duyệt/từ chối đơn
 */
@Path("/api/v1/prescriptions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Prescription API", description = "Các API tải ảnh đơn thuốc, duyệt đơn thuốc và tra cứu đơn")
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
public class PrescriptionResource {

    @Inject
    PrescriptionService prescriptionService;

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("KHACH_HANG")
    @Operation(summary = "Khách hàng tải lên ảnh đơn thuốc mới (Multipart)",
               description = "Yêu cầu tệp ảnh JPG hoặc PNG <= 5MB. Trạng thái ban đầu là CHO_DUYET.")
    @APIResponse(responseCode = "201", description = "Tạo đơn thuốc thành công")
    @APIResponse(responseCode = "400", description = "File sai định dạng hoặc vượt quá 5MB")
    @APIResponse(responseCode = "401", description = "Chưa xác thực")
    public Response uploadPrescription(PrescriptionUploadForm form) {
        PrescriptionResponse response = prescriptionService.createPrescription(form);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @GET
    @Path("/my-prescriptions")
    @RolesAllowed("KHACH_HANG")
    @Operation(summary = "Khách hàng xem danh sách đơn thuốc của chính mình",
               description = "Hỗ trợ phân trang và lọc theo trạng thái CHO_DUYET, DA_DUYET, TU_CHOI")
    public PrescriptionPageResponse getMyPrescriptions(
            @QueryParam("status") PrescriptionStatus status,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size
    ) {
        return prescriptionService.getMyPrescriptions(status, page, size);
    }

    @GET
    @Path("/pending")
    @RolesAllowed({"DUOC_SI", "ADMIN"})
    @Operation(summary = "Dược sĩ xem danh sách đơn thuốc đang chờ duyệt",
               description = "Danh sách đơn trạng thái CHO_DUYET, sắp xếp ưu tiên đơn gửi trước lên đầu (createdAt ASC)")
    public PrescriptionPageResponse getPendingPrescriptions(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size
    ) {
        return prescriptionService.getPendingPrescriptions(page, size);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"KHACH_HANG", "DUOC_SI", "ADMIN"})
    @Operation(summary = "Xem thông tin chi tiết một đơn thuốc kèm danh mục thuốc được duyệt",
               description = "Khách hàng chỉ xem được đơn của chính mình. Dược sĩ/Admin xem được mọi đơn.")
    public PrescriptionResponse getPrescriptionById(@PathParam("id") UUID id) {
        return prescriptionService.getPrescriptionById(id);
    }

    @GET
    @Path("/{id}/image")
    @RolesAllowed({"KHACH_HANG", "DUOC_SI", "ADMIN"})
    @Produces({"image/jpeg", "image/png"})
    @Operation(summary = "Tải/xem luồng ảnh đơn thuốc trực tiếp từ MinIO",
               description = "Khách hàng chỉ xem được ảnh đơn của mình. Dược sĩ/Admin xem được ảnh mọi đơn.")
    public Response getPrescriptionImage(@PathParam("id") UUID id) {
        PrescriptionService.PrescriptionImageData data = prescriptionService.getPrescriptionImage(id);
        return Response.ok(data.stream())
                .type(data.contentType())
                .build();
    }

    @PUT
    @Path("/{id}/approve")
    @RolesAllowed({"DUOC_SI", "ADMIN"})
    @Operation(summary = "Dược sĩ phê duyệt đơn thuốc",
               description = "Chỉ áp dụng cho đơn ở trạng thái CHO_DUYET. Chọn các thuốc và số lượng cho phép mua.")
    @APIResponse(responseCode = "200", description = "Duyệt đơn thành công")
    @APIResponse(responseCode = "400", description = "Dữ liệu danh mục thuốc rỗng hoặc không hợp lệ")
    @APIResponse(responseCode = "409", description = "Đơn thuốc đã được xử lý trước đó")
    public PrescriptionResponse approvePrescription(
            @PathParam("id") UUID id,
            @Valid ApprovePrescriptionRequest request
    ) {
        return prescriptionService.approvePrescription(id, request);
    }

    @PUT
    @Path("/{id}/reject")
    @RolesAllowed({"DUOC_SI", "ADMIN"})
    @Operation(summary = "Dược sĩ từ chối đơn thuốc",
               description = "Bắt buộc phải có lý do từ chối. Chỉ áp dụng cho đơn ở trạng thái CHO_DUYET.")
    @APIResponse(responseCode = "200", description = "Từ chối đơn thành công")
    @APIResponse(responseCode = "400", description = "Thiếu lý do từ chối")
    @APIResponse(responseCode = "409", description = "Đơn thuốc đã được xử lý trước đó")
    public PrescriptionResponse rejectPrescription(
            @PathParam("id") UUID id,
            @Valid RejectPrescriptionRequest request
    ) {
        return prescriptionService.rejectPrescription(id, request);
    }
}

