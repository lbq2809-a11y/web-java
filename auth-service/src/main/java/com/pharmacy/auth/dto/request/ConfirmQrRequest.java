package com.pharmacy.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ConfirmQrRequest(
        @NotBlank(message = "Session ID không được để trống")
        String sessionId
) {
}

