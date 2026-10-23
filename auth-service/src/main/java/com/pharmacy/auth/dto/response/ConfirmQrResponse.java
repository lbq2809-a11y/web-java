package com.pharmacy.auth.dto.response;

public record ConfirmQrResponse(
        String status
) {
    public static final ConfirmQrResponse CONFIRMED = new ConfirmQrResponse("CONFIRMED");
}

