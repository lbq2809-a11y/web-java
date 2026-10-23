package com.pharmacy.inventory.resource;

import com.pharmacy.inventory.dto.BatchResponse;
import com.pharmacy.inventory.dto.CreateBatchRequest;
import com.pharmacy.inventory.dto.PagedResponse;
import com.pharmacy.inventory.service.InventoryService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.UUID;

@Path("/api/v1/batches")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"ADMIN", "DUOC_SI"})
@Tag(name = "Batches", description = "Quản lý nhập lô thuốc mới và tra cứu danh sách lô theo FEFO")
@SecuritySchemes(value = {
        @SecurityScheme(
                securitySchemeName = "jwt",
                type = SecuritySchemeType.HTTP,
                scheme = "bearer",
                bearerFormat = "JWT"
        )
})
@SecurityRequirement(name = "jwt")
public class BatchResource {

    @Inject
    InventoryService inventoryService;

    @Inject
    JsonWebToken jwt;

    @POST
    @Operation(summary = "Nhập lô thuốc mới vào kho (Chỉ ADMIN và DUOC_SI)")
    public Response createBatch(@Valid CreateBatchRequest request, @Context SecurityContext sec) {
        String createdBy = jwt != null && jwt.getName() != null ? jwt.getName() :
                (sec.getUserPrincipal() != null ? sec.getUserPrincipal().getName() : "ADMIN");

        BatchResponse created = inventoryService.createBatch(request, createdBy);
        return Response.created(URI.create("/api/v1/batches/" + created.id()))
                .entity(created)
                .build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Xem chi tiết một lô thuốc theo ID")
    public BatchResponse getBatchById(@PathParam("id") UUID id) {
        return inventoryService.getBatchById(id);
    }

    @GET
    @Operation(summary = "Danh sách các lô thuốc (lọc theo medicineId, số ngày sắp hết hạn)")
    public PagedResponse<BatchResponse> searchBatches(
            @QueryParam("medicineId") String medicineId,
            @QueryParam("nearExpiryDays") Integer nearExpiryDays,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size
    ) {
        return inventoryService.searchBatches(medicineId, nearExpiryDays, page, size);
    }
}

