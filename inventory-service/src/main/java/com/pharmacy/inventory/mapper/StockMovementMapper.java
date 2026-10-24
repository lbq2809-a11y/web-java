package com.pharmacy.inventory.mapper;

import com.pharmacy.inventory.dto.StockMovementResponse;
import com.pharmacy.inventory.entity.StockMovement;

public class StockMovementMapper {

    public static StockMovementResponse toResponse(StockMovement m) {
        if (m == null) return null;
        return new StockMovementResponse(
                m.id,
                m.medicineId,
                m.batchId,
                m.type,
                m.quantity,
                m.orderId,
                m.reason,
                m.createdBy,
                m.createdAt
        );
    }
}

