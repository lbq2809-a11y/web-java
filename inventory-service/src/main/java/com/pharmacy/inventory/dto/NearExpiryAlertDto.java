package com.pharmacy.inventory.dto;

import java.time.LocalDate;
import java.util.UUID;

public record NearExpiryAlertDto(
        UUID batchId,
        String medicineId,
        String medicineName,
        String batchNumber,
        LocalDate expiryDate,
        int quantity,
        long daysLeft
) {}

