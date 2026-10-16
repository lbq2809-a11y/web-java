package com.pharmacy.medicine.resource;

import com.pharmacy.medicine.dto.CategoryRequest;
import com.pharmacy.medicine.dto.CategoryResponse;
import com.pharmacy.medicine.service.CategoryService;
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
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * REST API quản lý danh mục nhóm thuốc (/api/v1/categories).
 */
@Path("/api/v1/categories")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Categories", description = "Quản lý danh mục nhóm thuốc")
public class CategoryResource {

    @Inject
    CategoryService categoryService;

    @GET
    @PermitAll
    @Operation(summary = "Lấy danh sách danh mục thuốc", description = "Ai cũng xem được danh mục đang hoạt động. ADMIN/DUOC_SI có thể truyền includeInactive=true để xem cả danh mục ẩn.")
    public List<CategoryResponse> listCategories(
            @QueryParam("includeInactive") @DefaultValue("false") boolean includeInactive,
            @Context SecurityContext securityContext
    ) {
        boolean canSeeInactive = includeInactive && isStaffOrAdmin(securityContext);
        return categoryService.listCategories(canSeeInactive);
    }

    @GET
    @Path("/{id}")
    @PermitAll
    @Operation(summary = "Xem chi tiết một danh mục thuốc theo ID")
    public CategoryResponse getCategoryById(@PathParam("id") String id) {
        return categoryService.getCategoryById(id);
    }

    @POST
    @RolesAllowed({"ADMIN", "DUOC_SI"})
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Tạo danh mục thuốc mới (Chỉ ADMIN hoặc DUOC_SI)")
    public Response createCategory(@Valid CategoryRequest request) {
        CategoryResponse created = categoryService.createCategory(request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "DUOC_SI"})
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cập nhật danh mục thuốc (Chỉ ADMIN hoặc DUOC_SI)")
    public CategoryResponse updateCategory(@PathParam("id") String id, @Valid CategoryRequest request) {
        return categoryService.updateCategory(id, request);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Xóa mềm / ẩn danh mục thuốc (Chỉ ADMIN)")
    public Response deleteCategory(@PathParam("id") String id) {
        categoryService.deleteCategory(id);
        return Response.noContent().build();
    }

    private boolean isStaffOrAdmin(SecurityContext securityContext) {
        if (securityContext == null) {
            return false;
        }
        return securityContext.isUserInRole("ADMIN") || securityContext.isUserInRole("DUOC_SI");
    }
}

