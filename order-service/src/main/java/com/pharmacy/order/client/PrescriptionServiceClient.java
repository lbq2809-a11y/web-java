package com.pharmacy.order.client;

import com.pharmacy.order.dto.LinkOrderRequest;
import com.pharmacy.order.dto.LinkOrderResponse;
import com.pharmacy.order.dto.ValidatePrescriptionRequest;
import com.pharmacy.order.dto.ValidatePrescriptionResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * REST Client gọi sang prescription-service (Cổng 8083) để:
 * 1. Kiểm tra đơn thuốc có hợp lệ cho các thuốc kê đơn (ETC) không.
 * 2. Gắn đơn thuốc vào đơn hàng sau khi tạo đơn thành công.
 */
@Path("/api/v1/prescriptions")
@RegisterRestClient(configKey = "prescription-service")
@RegisterClientHeaders(HeaderPropagationFactory.class)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface PrescriptionServiceClient {

    @POST
    @Path("/{id}/validate-for-order")
    @Timeout(value = 3, unit = ChronoUnit.SECONDS)
    @Retry(maxRetries = 2, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000)
    ValidatePrescriptionResponse validateForOrder(
            @PathParam("id") UUID id,
            ValidatePrescriptionRequest request
    );

    @POST
    @Path("/{id}/order-links")
    @Timeout(value = 3, unit = ChronoUnit.SECONDS)
    @Retry(maxRetries = 2, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000)
    LinkOrderResponse linkOrder(
            @PathParam("id") UUID id,
            LinkOrderRequest request
    );
}

