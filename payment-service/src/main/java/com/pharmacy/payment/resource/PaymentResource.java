package com.pharmacy.payment.resource;

import com.pharmacy.payment.dto.CreatePaymentRequest;
import com.pharmacy.payment.dto.PaymentResponse;
import com.pharmacy.payment.service.PaymentService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/api/v1/payments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PaymentResource {

    @Inject
    PaymentService paymentService;

    @POST
    @RolesAllowed({"KHACH_HANG", "DUOC_SI", "ADMIN"})
    public PaymentResponse createPayment(
        @Valid CreatePaymentRequest request,
        @HeaderParam("Authorization") String authorization,
        @HeaderParam("X-Correlation-Id") String correlationId,
        @HeaderParam("X-Forwarded-For") String forwardedFor
    ) {
        String clientIp = forwardedFor;

        if (clientIp != null && clientIp.contains(",")) {
            clientIp = clientIp.split(",")[0].trim();
        }

        return paymentService.createPayment(
            request,
            authorization,
            correlationId,
            clientIp
        );
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"KHACH_HANG", "DUOC_SI", "ADMIN"})
    public PaymentResponse getPayment(
        @PathParam("id") UUID id,
        @HeaderParam("Authorization") String authorization,
        @HeaderParam("X-Correlation-Id") String correlationId
    ) {
        return paymentService.getPayment(id, authorization, correlationId);
    }

    @POST
    @Path("/{id}/confirm-cod")
    @RolesAllowed({"ADMIN", "DUOC_SI"})
    public PaymentResponse confirmCod(
        @PathParam("id") UUID id,
        @HeaderParam("X-Correlation-Id") String correlationId
    ) {
        return paymentService.confirmCod(id, correlationId);
    }
}