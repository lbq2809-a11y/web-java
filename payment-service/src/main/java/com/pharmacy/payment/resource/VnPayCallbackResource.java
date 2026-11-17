package com.pharmacy.payment.resource;

import com.pharmacy.payment.dto.VnPayIpnResponse;
import com.pharmacy.payment.dto.VnPayReturnResponse;
import com.pharmacy.payment.service.VnPayCallbackService;
import com.pharmacy.payment.service.VnPayService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import java.util.Map;
import java.util.TreeMap;

@Path("/api/v1/payments")
@Produces(MediaType.APPLICATION_JSON)
public class VnPayCallbackResource {

    @Inject
    VnPayCallbackService callbackService;

    @Inject
    VnPayService vnPayService;

    @GET
    @Path("/vnpay-ipn")
    @PermitAll
    public VnPayIpnResponse ipn(@Context UriInfo uriInfo) {
        return callbackService.processIpn(readParams(uriInfo));
    }

    @GET
    @Path("/vnpay-return")
    @PermitAll
    public VnPayReturnResponse paymentReturn(
        @Context UriInfo uriInfo
    ) {
        Map<String, String> params = readParams(uriInfo);

        boolean verified =
            vnPayService.verifySignature(params)
            && vnPayService.isMerchantCode(params.get("vnp_TmnCode"));

        return new VnPayReturnResponse(
            verified,
            verified ? params.get("vnp_ResponseCode") : "97",
            verified ? params.get("vnp_TransactionStatus") : null,
            verified ? params.get("vnp_TxnRef") : null,
            verified
                ? "Đã xác minh dữ liệu Return URL. Trạng thái chính thức được cập nhật qua IPN."
                : "Chữ ký Return URL không hợp lệ"
        );
    }

    private Map<String, String> readParams(UriInfo uriInfo) {
        Map<String, String> params = new TreeMap<>();

        uriInfo.getQueryParameters().forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                params.put(key, values.get(0));
            }
        });

        return params;
    }
}