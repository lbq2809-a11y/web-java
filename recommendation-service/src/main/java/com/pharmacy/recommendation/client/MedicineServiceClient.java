package com.pharmacy.recommendation.client;

import com.pharmacy.recommendation.dto.MedicineResponse;
import com.pharmacy.recommendation.dto.PagedResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * REST Client gọi sang medicine-service (cổng 8081).
 * Hỗ trợ Timeout (3s), Retry và CircuitBreaker.
 */
@Path("/api/v1/medicines")
@RegisterRestClient(configKey = "medicine-service")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface MedicineServiceClient {

    @GET
    @Path("/{id}")
    @Timeout(value = 3, unit = ChronoUnit.SECONDS)
    @Retry(maxRetries = 2, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000)
    MedicineResponse getMedicineById(@PathParam("id") String id);

    @GET
    @Path("/batch")
    @Timeout(value = 3, unit = ChronoUnit.SECONDS)
    @Retry(maxRetries = 2, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000)
    List<MedicineResponse> getMedicinesBatch(@QueryParam("ids") String ids);

    @GET
    @Timeout(value = 3, unit = ChronoUnit.SECONDS)
    @Retry(maxRetries = 2, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000)
    PagedResponse<MedicineResponse> getMedicinesByCategory(
            @QueryParam("categoryId") String categoryId,
            @QueryParam("page") int page,
            @QueryParam("size") int size
    );
}

