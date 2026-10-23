package com.pharmacy.inventory.repository;

import com.pharmacy.inventory.entity.MovementType;
import com.pharmacy.inventory.entity.StockMovement;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class StockMovementRepository implements PanacheRepositoryBase<StockMovement, UUID> {

    public List<StockMovement> searchMovements(String medicineId, UUID batchId, MovementType type, int page, int size) {
        StringBuilder query = new StringBuilder("1 = 1");
        Parameters params = new Parameters();

        if (medicineId != null && !medicineId.isBlank()) {
            query.append(" and medicineId = :medicineId");
            params.and("medicineId", medicineId.trim());
        }
        if (batchId != null) {
            query.append(" and batchId = :batchId");
            params.and("batchId", batchId);
        }
        if (type != null) {
            query.append(" and type = :type");
            params.and("type", type);
        }
        query.append(" order by createdAt desc");

        return find(query.toString(), params).page(Page.of(page, size)).list();
    }

    public long countSearchMovements(String medicineId, UUID batchId, MovementType type) {
        StringBuilder query = new StringBuilder("1 = 1");
        Parameters params = new Parameters();

        if (medicineId != null && !medicineId.isBlank()) {
            query.append(" and medicineId = :medicineId");
            params.and("medicineId", medicineId.trim());
        }
        if (batchId != null) {
            query.append(" and batchId = :batchId");
            params.and("batchId", batchId);
        }
        if (type != null) {
            query.append(" and type = :type");
            params.and("type", type);
        }

        return count(query.toString(), params);
    }
}

