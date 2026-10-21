package com.pharmacy.prescription.repository;

import com.pharmacy.prescription.entity.Prescription;
import com.pharmacy.prescription.entity.PrescriptionStatus;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PrescriptionRepository implements PanacheRepositoryBase<Prescription, UUID> {

    /**
     * Tìm đơn thuốc theo ID kèm theo danh sách items (tránh LazyInitializationException khi thoát transaction)
     */
    public Optional<Prescription> findByIdWithItems(UUID id) {
        return find("SELECT DISTINCT p FROM Prescription p LEFT JOIN FETCH p.items WHERE p.id = ?1", id)
                .firstResultOptional();
    }

    /**
     * Lấy danh sách đơn thuốc của một khách hàng cụ thể (hỗ trợ phân trang và lọc theo trạng thái)
     */
    public List<Prescription> findByCustomerId(UUID customerId, PrescriptionStatus status, int page, int size) {
        PanacheQuery<Prescription> query;
        if (status != null) {
            query = find("customerId = ?1 and status = ?2", Sort.by("createdAt").descending(), customerId, status);
        } else {
            query = find("customerId = ?1", Sort.by("createdAt").descending(), customerId);
        }
        return query.page(Page.of(page, size)).list();
    }

    /**
     * Đếm tổng số đơn thuốc của khách hàng
     */
    public long countByCustomerId(UUID customerId, PrescriptionStatus status) {
        if (status != null) {
            return count("customerId = ?1 and status = ?2", customerId, status);
        }
        return count("customerId = ?1", customerId);
    }

    /**
     * Lấy danh sách đơn thuốc đang chờ dược sĩ xử lý (CHO_DUYET), ưu tiên đơn gửi trước xử lý trước (createdAt ASC)
     */
    public List<Prescription> findPendingPrescriptions(int page, int size) {
        return find("status = ?1", Sort.by("createdAt").ascending(), PrescriptionStatus.CHO_DUYET)
                .page(Page.of(page, size))
                .list();
    }

    /**
     * Đếm tổng số đơn thuốc đang chờ xử lý
     */
    public long countPending() {
        return count("status = ?1", PrescriptionStatus.CHO_DUYET);
    }
}

