package com.pharmacy.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        String code,
        String message,
        List<String> details
) {
    public ErrorResponse(String code, String message) {
        this(code, message, Collections.emptyList());
    }
}

