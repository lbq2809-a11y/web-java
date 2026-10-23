package com.pharmacy.inventory.repository;

import com.pharmacy.inventory.entity.AlertType;
import com.pharmacy.inventory.entity.StockAlert;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.UUID;

@ApplicationScoped
public class StockAlertRepository implements PanacheRepositoryBase<StockAlert, UUID> {

    public boolean existsAlertToday(AlertType alertType, String medicineId, UUID batchId, LocalDate alertDate) {
        UUID effectiveBatchId = batchId != null ? batchId : StockAlert.DEFAULT_BATCH_ID;
        return count("alertType = ?1 and medicineId = ?2 and batchId = ?3 and alertDate = ?4",
                alertType, medicineId, effectiveBatchId, alertDate) > 0;
    }
}

