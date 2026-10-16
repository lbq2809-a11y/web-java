package com.pharmacy.medicine.resource;

import com.pharmacy.medicine.document.PrescriptionType;
import com.pharmacy.medicine.dto.MedicineRequest;
import com.pharmacy.medicine.dto.MedicineResponse;
import com.pharmacy.medicine.dto.PagedResponse;
import com.pharmacy.medicine.service.MedicineService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST API quản lý và tra cứu thuốc (/api/v1/medicines).
 */
@Path("/api/v1/medicines")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Medicines", description = "Tra cứu, lọc, lấy theo lô (batch) và quản lý danh mục thuốc (OTC/ETC)")
public class MedicineResource {

    @Inject
    MedicineService medicineService;

    @GET
    @PermitAll
    @Operation(
            summary = "Tìm kiếm, lọc, sắp xếp và phân trang thuốc",
            description = "Hỗ trợ tìm theo từ khóa (tên, mã, hoạt chất, chỉ định), lọc theo danh mục, loại thuốc (OTC/ETC), khoảng giá và sắp xếp."
    )
    public PagedResponse<MedicineResponse> searchMedicines(
            @QueryParam("keyword") String keyword,
            @QueryParam("categoryId") String categoryId,
            @QueryParam("prescriptionType") PrescriptionType prescriptionType,
            @QueryParam("minPrice") BigDecimal minPrice,
            @QueryParam("maxPrice") BigDecimal maxPrice,
            @QueryParam("includeInactive") @DefaultValue("false") boolean includeInactive,
            @QueryParam("sortBy") @DefaultValue("name") String sortBy,
            @QueryParam("sortDir") @DefaultValue("asc") String sortDir,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @Context SecurityContext securityContext
    ) {
        boolean canSeeInactive = includeInactive && isStaffOrAdmin(securityContext);
        return medicineService.searchMedicines(
                keyword, categoryId, prescriptionType, minPrice, maxPrice, canSeeInactive, sortBy, sortDir, page, size
        );
    }

    @GET
    @Path("/batch")
    @PermitAll
    @Operation(
            summary = "Lấy thông tin nhiều thuốc cùng lúc theo danh sách ID (tối đa 50 ID)",
            description = "Phục vụ order-service / prescription-service lấy thông tin giá và loại thuốc (OTC/ETC) hàng loạt."
    )
    public List<MedicineResponse> getMedicinesBatch(
            @Parameter(description = "Danh sách ID thuốc cách nhau bởi dấu phẩy (tối đa 50 ID)", required = true)
            @QueryParam("ids") String ids
    ) {
        return medicineService.getMedicinesBatch(ids);
    }

    @GET
    @Path("/{id}")
    @PermitAll
    @Operation(summary = "Xem chi tiết một thuốc theo ID")
    public MedicineResponse getMedicineById(
            @PathParam("id") String id,
            @Context SecurityContext securityContext
    ) {
        return medicineService.getMedicineById(id, isStaffOrAdmin(securityContext));
    }

    @POST
    @RolesAllowed({"ADMIN", "DUOC_SI"})
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Thêm thuốc mới (Chỉ ADMIN hoặc DUOC_SI)")
    public Response createMedicine(@Valid MedicineRequest request) {
        MedicineResponse created = medicineService.createMedicine(request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "DUOC_SI"})
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cập nhật thông tin thuốc (Chỉ ADMIN hoặc DUOC_SI)")
    public MedicineResponse updateMedicine(@PathParam("id") String id, @Valid MedicineRequest request) {
        return medicineService.updateMedicine(id, request);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Ẩn / ngừng kinh doanh thuốc (Chỉ ADMIN)")
    public Response deleteMedicine(@PathParam("id") String id) {
        medicineService.deleteMedicine(id);
        return Response.noContent().build();
    }

    private boolean isStaffOrAdmin(SecurityContext securityContext) {
        if (securityContext == null) {
            return false;
        }
        return securityContext.isUserInRole("ADMIN") || securityContext.isUserInRole("DUOC_SI");
    }
}

