package com.pharmacy.prescription.repository;

import com.pharmacy.prescription.entity.PrescriptionOrderLink;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PrescriptionOrderLinkRepository implements PanacheRepositoryBase<PrescriptionOrderLink, UUID> {

    /**
     * Tìm liên kết đơn thuốc theo orderId
     */
    public Optional<PrescriptionOrderLink> findByOrderId(UUID orderId) {
        return find("orderId", orderId).firstResultOptional();
    }

    /**
     * Tìm liên kết đơn thuốc theo prescriptionId
     */
    public Optional<PrescriptionOrderLink> findByPrescriptionId(UUID prescriptionId) {
        return find("prescription.id", prescriptionId).firstResultOptional();
    }

    /**
     * Kiểm tra xem đơn thuốc này đã được sử dụng cho đơn hàng nào chưa
     */
    public boolean existsByPrescriptionId(UUID prescriptionId) {
        return count("prescription.id", prescriptionId) > 0;
    }
}

