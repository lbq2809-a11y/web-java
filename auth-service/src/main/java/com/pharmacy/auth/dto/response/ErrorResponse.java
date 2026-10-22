package com.pharmacy.auth.dto.response;

import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        List<String> details
) {
}

