package com.pharmacy.consultation.client;

import com.pharmacy.consultation.dto.MedicineResponse;
import com.pharmacy.consultation.dto.PagedResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST Client gọi sang medicine-service để lấy danh sách thuốc có phân trang.
 */
@RegisterRestClient(configKey = "medicine-service")
@Path("/api/v1/medicines")
public interface MedicineServiceClient {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    PagedResponse<MedicineResponse> getMedicines(
            @QueryParam("page") int page,
            @QueryParam("size") int size,
            @QueryParam("includeInactive") boolean includeInactive
    );
}

