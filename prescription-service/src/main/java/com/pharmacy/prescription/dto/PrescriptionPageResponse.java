package com.pharmacy.prescription.dto;

import java.util.List;

public record PrescriptionPageResponse(
        List<PrescriptionResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}

