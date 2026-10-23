package com.pharmacy.auth.dto.response;

import java.time.Instant;

public record CreateQrSessionResponse(
        String sessionId,
        String pollSecret,
        String qrContent,
        String status,
        Instant expiresAt
) {
}

