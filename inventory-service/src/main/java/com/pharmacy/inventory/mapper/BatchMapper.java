package com.pharmacy.inventory.mapper;

import com.pharmacy.inventory.dto.BatchResponse;
import com.pharmacy.inventory.entity.Batch;

public class BatchMapper {

    public static BatchResponse toResponse(Batch batch) {
        if (batch == null) return null;
        return new BatchResponse(
                batch.id,
                batch.medicineId,
                batch.batchNumber,
                batch.expiryDate,
                batch.quantityIn,
                batch.quantityAvailable,
                batch.costPrice,
                batch.receivedAt,
                batch.createdAt,
                batch.updatedAt
        );
    }
}

