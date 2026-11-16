package com.pharmacy.payment.client;

import com.pharmacy.payment.dto.OrderSnapshot;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.util.UUID;

@Path("/api/v1/orders")
@RegisterRestClient(configKey = "order-service")
@Produces(MediaType.APPLICATION_JSON)
public interface OrderServiceClient {

    @GET
    @Path("/{id}")
    OrderSnapshot getOrder(
        @PathParam("id") UUID orderId,
        @HeaderParam("Authorization") String authorization,
        @HeaderParam("X-Correlation-Id") String correlationId
    );
}