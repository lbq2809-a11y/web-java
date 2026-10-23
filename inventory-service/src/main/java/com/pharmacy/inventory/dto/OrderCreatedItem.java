package com.pharmacy.inventory.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedItem(
        String medicineId,
        String medicineName,
        String prescriptionType,
        BigDecimal unitPrice,
        int quantity
) {}

