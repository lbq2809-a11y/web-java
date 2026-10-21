package com.pharmacy.order.dto;

import java.util.List;
import java.util.UUID;

public record ValidatePrescriptionRequest(
        UUID customerId,
        List<ValidateItemRequest> items
) {
}

