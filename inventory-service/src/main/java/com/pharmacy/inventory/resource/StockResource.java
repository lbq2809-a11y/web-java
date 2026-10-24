package com.pharmacy.inventory.resource;

import com.pharmacy.inventory.dto.BatchResponse;
import com.pharmacy.inventory.dto.PagedResponse;
import com.pharmacy.inventory.dto.StockAdjustmentRequest;
import com.pharmacy.inventory.dto.StockAlertsResponse;
import com.pharmacy.inventory.dto.StockCheckRequest;
import com.pharmacy.inventory.dto.StockCheckResponse;
import com.pharmacy.inventory.dto.StockResponse;
import com.pharmacy.inventory.dto.StockSummaryResponse;
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
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/stocks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"ADMIN", "DUOC_SI"})
@Tag(name = "Stocks", description = "Quản lý tồn kho theo thuốc, điều chỉnh kho, kiểm tra tồn và xem cảnh báo")
@SecurityRequirement(name = "jwt")
public class StockResource {

    @Inject
    InventoryService inventoryService;

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/{medicineId}")
    @Operation(summary = "Xem tổng tồn khả dụng và chi tiết các lô của 1 loại thuốc")
    public StockResponse getStockByMedicineId(@PathParam("medicineId") String medicineId) {
        return inventoryService.getStockByMedicineId(medicineId);
    }

    @GET
    @Operation(summary = "Báo cáo tổng hợp tồn kho toàn bộ thuốc")
    public PagedResponse<StockSummaryResponse> getStockSummary(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size
    ) {
        return inventoryService.getStockSummary(page, size);
    }

    @POST
    @Path("/adjust")
    @Operation(summary = "Điều chỉnh tồn kho lô thuốc có lý do (Kiểm kê, hư hỏng, thất thoát)")
    public BatchResponse adjustStock(@Valid StockAdjustmentRequest request, @Context SecurityContext sec) {
        String createdBy = jwt != null && jwt.getName() != null ? jwt.getName() :
                (sec.getUserPrincipal() != null ? sec.getUserPrincipal().getName() : "ADMIN");
        return inventoryService.adjustStock(request, createdBy);
    }

    @POST
    @Path("/check")
    @Operation(summary = "Kiểm tra tồn kho cho danh sách mặt hàng (API nội bộ)")
    public StockCheckResponse checkStock(@Valid StockCheckRequest request) {
        return inventoryService.checkStock(request);
    }

    @GET
    @Path("/alerts")
    @Operation(summary = "Xem danh sách cảnh báo hiện tại (Lô sắp hết hạn và thuốc dưới ngưỡng tồn)")
    public StockAlertsResponse getAlerts() {
        return inventoryService.getActiveAlerts();
    }
}

