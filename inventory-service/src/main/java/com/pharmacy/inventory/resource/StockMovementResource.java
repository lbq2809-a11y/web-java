package com.pharmacy.inventory.resource;

import com.pharmacy.inventory.dto.PagedResponse;
import com.pharmacy.inventory.dto.StockMovementResponse;
import com.pharmacy.inventory.entity.MovementType;
import com.pharmacy.inventory.service.InventoryService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

@Path("/api/v1/stock-movements")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"ADMIN", "DUOC_SI"})
@Tag(name = "Stock Movements", description = "Sổ cái theo dõi toàn bộ lịch sử xuất, nhập, giữ, nhả và điều chỉnh kho")
@SecurityRequirement(name = "jwt")
public class StockMovementResource {

    @Inject
    InventoryService inventoryService;

    @GET
    @Operation(summary = "Xem lịch sử biến động kho (lọc theo medicineId, batchId, loại biến động)")
    public PagedResponse<StockMovementResponse> searchMovements(
            @QueryParam("medicineId") String medicineId,
            @QueryParam("batchId") UUID batchId,
            @QueryParam("type") MovementType type,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size
    ) {
        return inventoryService.searchMovements(medicineId, batchId, type, page, size);
    }
}

