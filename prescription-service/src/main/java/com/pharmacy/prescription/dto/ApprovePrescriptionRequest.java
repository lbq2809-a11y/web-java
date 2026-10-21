package com.pharmacy.prescription.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.List;

public record ApprovePrescriptionRequest(
        Instant expiresAt,

        @NotEmpty(message = "Danh sách thuốc được duyệt không được để trống")
        @Valid
        List<ApproveItemRequest> items
) {
}

