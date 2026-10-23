package com.pharmacy.inventory.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
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
 * REST Client gọi sang medicine-service (Cổng 8081) để tra cứu thông tin tên thuốc theo lô (Batch)
 */
@Path("/api/v1/medicines")
@RegisterRestClient(configKey = "medicine-service")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface MedicineServiceClient {

    @GET
    @Path("/batch")
    @Timeout(value = 3, unit = ChronoUnit.SECONDS)
    @Retry(maxRetries = 2, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000)
    List<MedicineResponse> getMedicinesBatch(@QueryParam("ids") String ids);
}

