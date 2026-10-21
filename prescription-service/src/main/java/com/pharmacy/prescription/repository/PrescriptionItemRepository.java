package com.pharmacy.prescription.repository;

import com.pharmacy.prescription.entity.PrescriptionItem;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PrescriptionItemRepository implements PanacheRepositoryBase<PrescriptionItem, UUID> {

    /**
     * Tìm danh sách các thuốc trong đơn theo prescriptionId
     */
    public List<PrescriptionItem> findByPrescriptionId(UUID prescriptionId) {
        return list("prescription.id", prescriptionId);
    }

    /**
     * Tìm 1 loại thuốc cụ thể trong đơn thuốc
     */
    public Optional<PrescriptionItem> findByPrescriptionIdAndMedicineId(UUID prescriptionId, String medicineId) {
        return find("prescription.id = ?1 and medicineId = ?2", prescriptionId, medicineId).firstResultOptional();
    }
}

