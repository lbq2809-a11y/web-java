package com.pharmacy.inventory.repository;

import com.pharmacy.inventory.entity.Batch;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class BatchRepository implements PanacheRepositoryBase<Batch, UUID> {

    public Optional<Batch> findByMedicineIdAndBatchNumber(String medicineId, String batchNumber) {
        return find("medicineId = ?1 and batchNumber = ?2", medicineId, batchNumber).firstResultOptional();
    }

    /**
     * Khóa dòng (SELECT ... FOR UPDATE) các lô thuốc khả dụng theo nguyên tắc FEFO:
     * - Chỉ lấy các lô còn hạn (expiryDate > today)
     * - Chỉ lấy các lô có tồn khả dụng (quantityAvailable > 0)
     * - Sắp xếp theo ngày hết hạn sớm nhất (expiryDate ASC), sau đó theo ngày nhập (receivedAt ASC)
     */
    public List<Batch> findAvailableBatchesForUpdate(String medicineId, LocalDate today) {
        return find("medicineId = ?1 and expiryDate > ?2 and quantityAvailable > 0 order by expiryDate asc, receivedAt asc",
                medicineId, today)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .list();
    }

    public Optional<Batch> findByIdForUpdate(UUID id) {
        return find("id = ?1", id)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }

    public List<Batch> findByMedicineId(String medicineId) {
        return find("medicineId = ?1 order by expiryDate asc", medicineId).list();
    }

    public List<Batch> findNearExpiryBatches(LocalDate today, LocalDate targetDate) {
        return find("quantityAvailable > 0 and expiryDate > ?1 and expiryDate <= ?2 order by expiryDate asc",
                today, targetDate).list();
    }

    public List<Batch> searchBatches(String medicineId, Integer nearExpiryDays, int page, int size) {
        StringBuilder query = new StringBuilder("1 = 1");
        if (medicineId != null && !medicineId.isBlank()) {
            query.append(" and medicineId = '").append(medicineId.trim()).append("'");
        }
        if (nearExpiryDays != null && nearExpiryDays > 0) {
            LocalDate today = LocalDate.now();
            LocalDate limitDate = today.plusDays(nearExpiryDays);
            query.append(" and expiryDate <= '").append(limitDate).append("' and quantityAvailable > 0");
        }
        query.append(" order by expiryDate asc, receivedAt desc");

        return find(query.toString()).page(Page.of(page, size)).list();
    }

    public long countSearchBatches(String medicineId, Integer nearExpiryDays) {
        StringBuilder query = new StringBuilder("1 = 1");
        if (medicineId != null && !medicineId.isBlank()) {
            query.append(" and medicineId = '").append(medicineId.trim()).append("'");
        }
        if (nearExpiryDays != null && nearExpiryDays > 0) {
            LocalDate today = LocalDate.now();
            LocalDate limitDate = today.plusDays(nearExpiryDays);
            query.append(" and expiryDate <= '").append(limitDate).append("' and quantityAvailable > 0");
        }
        return count(query.toString());
    }

    /**
     * Tính tổng số lượng khả dụng từ tất cả các lô chưa hết hạn của 1 loại thuốc
     */
    public long sumAvailableQuantity(String medicineId, LocalDate today) {
        Long sum = find("select coalesce(sum(b.quantityAvailable), 0) from Batch b where b.medicineId = ?1 and b.expiryDate > ?2",
                medicineId, today).project(Long.class).firstResult();
        return sum != null ? sum : 0L;
    }
}

