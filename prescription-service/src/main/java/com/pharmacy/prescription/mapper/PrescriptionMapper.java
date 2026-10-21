package com.pharmacy.prescription.mapper;

import com.pharmacy.prescription.dto.PrescriptionItemDto;
import com.pharmacy.prescription.dto.PrescriptionResponse;
import com.pharmacy.prescription.entity.Prescription;
import com.pharmacy.prescription.entity.PrescriptionItem;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class PrescriptionMapper {

    public PrescriptionResponse toResponse(Prescription entity) {
        if (entity == null) {
            return null;
        }

        List<PrescriptionItemDto> itemDtos = Collections.emptyList();
        if (entity.getItems() != null) {
            itemDtos = entity.getItems().stream()
                    .map(this::toItemDto)
                    .toList();
        }

        return new PrescriptionResponse(
                entity.getId(),
                entity.getCustomerId(),
                entity.getCustomerName(),
                entity.getCustomerEmail(),
                entity.getImagePath(),
                entity.getNotes(),
                entity.getStatus(),
                entity.getPharmacistId(),
                entity.getPharmacistName(),
                entity.getRejectionReason(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getReviewedAt(),
                entity.getUpdatedAt(),
                itemDtos
        );
    }

    public PrescriptionItemDto toItemDto(PrescriptionItem item) {
        if (item == null) {
            return null;
        }
        return new PrescriptionItemDto(
                item.getId(),
                item.getMedicineId(),
                item.getMedicineName(),
                item.getMaxAllowedQuantity()
        );
    }
}

