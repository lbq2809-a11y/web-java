package com.pharmacy.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QrSessionStatusResponse(
        String sessionId,
        String status,
        String accessToken,
        String tokenType,
        Long expiresIn,
        UserResponse user
) {
    public static QrSessionStatusResponse pending(String sessionId) {
        return new QrSessionStatusResponse(sessionId, "PENDING", null, null, null, null);
    }

    public static QrSessionStatusResponse confirmed(String sessionId, String accessToken, String tokenType, long expiresIn, UserResponse user) {
        return new QrSessionStatusResponse(sessionId, "CONFIRMED", accessToken, tokenType, expiresIn, user);
    }
}

