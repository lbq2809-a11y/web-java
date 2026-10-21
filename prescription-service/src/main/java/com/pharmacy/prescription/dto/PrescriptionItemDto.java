package com.pharmacy.prescription.dto;

import java.util.UUID;

public record PrescriptionItemDto(
        UUID id,
        String medicineId,
        String medicineName,
        Integer maxAllowedQuantity
) {
}

