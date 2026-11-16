package com.pharmacy.inventory.service;

import com.pharmacy.inventory.client.MedicinePagedResponse;
import com.pharmacy.inventory.client.MedicineResponse;
import com.pharmacy.inventory.client.MedicineServiceClient;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Khởi tạo dữ liệu tồn kho mẫu khi inventory-service khởi động:
 * đồng bộ danh sách thuốc từ medicine-service và tạo sẵn 1-2 lô hàng còn hạn (2027-2028)
 * cho các thuốc chưa có tồn kho khả dụng trong inventory_db.
 */
@ApplicationScoped
public class InventoryDataInitializer {

    private static final Logger LOG = Logger.getLogger(InventoryDataInitializer.class);
    private static final int PAGE_SIZE = 50;
    private static final int MAX_PAGES_GUARD = 20;

    @Inject
    @RestClient
    MedicineServiceClient medicineServiceClient;

    @Inject
    InventoryService inventoryService;

    @ConfigProperty(name = "inventory.seed.enabled", defaultValue = "true")
    boolean seedEnabled;

    private final AtomicBoolean initialSeedCompleted = new AtomicBoolean(false);

    void onStart(@Observes StartupEvent event) {
        if (!seedEnabled) {
            LOG.info("Tự động khởi tạo dữ liệu kho mẫu đang tắt (inventory.seed.enabled=false).");
            return;
        }
        seedInitialStockSafely("khởi động");
    }

    /**
     * Tự động thử lại định kỳ nếu lúc inventory-service vừa bật mà medicine-service chưa sẵn sàng.
     * Khi đã seed thành công ít nhất một lượt từ medicine-service thì bỏ qua ngay lập tức.
     */
    @Scheduled(every = "{inventory.seed.retry-interval:30s}", delayed = "15s")
    void retrySeedIfPending() {
        if (!seedEnabled || initialSeedCompleted.get()) {
            return;
        }
        seedInitialStockSafely("thử lại định kỳ");
    }

    private void seedInitialStockSafely(String trigger) {
        try {
            boolean done = seedInitialStock();
            if (!done) {
                LOG.infof("Chưa hoàn tất khởi tạo kho mẫu ở pha %s (medicine-service chưa sẵn sàng hoặc chưa có thuốc). Hệ thống sẽ tự thử lại.", trigger);
            }
        } catch (Exception e) {
            LOG.warnf("Bỏ qua lỗi khởi tạo dữ liệu kho mẫu (%s): %s", trigger, e.getMessage());
        }
    }

    /**
     * Quét toàn bộ thuốc đang hoạt động từ medicine-service và tạo 1-2 lô hàng mẫu
     * cho những thuốc chưa có tồn kho khả dụng.
     *
     * @return true nếu lấy được danh sách thuốc từ medicine-service và xử lý xong
     */
    public boolean seedInitialStock() {
        int page = 0;
        int fetchedMedicines = 0;
        int seededMedicines = 0;
        int createdBatches = 0;

        while (page < MAX_PAGES_GUARD) {
            MedicinePagedResponse response;
            try {
                response = medicineServiceClient.getMedicines(page, PAGE_SIZE, false);
            } catch (Exception e) {
                LOG.warnf("Không thể kết nối medicine-service để lấy danh sách thuốc (page=%d): %s", page, e.getMessage());
                return false;
            }

            if (response == null || response.items == null || response.items.isEmpty()) {
                break;
            }

            for (MedicineResponse med : response.items) {
                fetchedMedicines++;
                try {
                    int batches = inventoryService.seedInitialStockForMedicine(med);
                    if (batches > 0) {
                        seededMedicines++;
                        createdBatches += batches;
                    }
                } catch (Exception e) {
                    LOG.warnf("Không thể khởi tạo kho mẫu cho thuốc %s: %s",
                            med != null ? med.id : "null", e.getMessage());
                }
            }

            if (page + 1 >= response.totalPages || response.items.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }

        if (fetchedMedicines == 0) {
            return false;
        }

        initialSeedCompleted.set(true);
        LOG.infof("Khởi tạo kho mẫu hoàn tất: đã kiểm tra %d thuốc, bổ sung %d lô cho %d thuốc chưa có tồn kho.",
                fetchedMedicines, createdBatches, seededMedicines);
        return true;
    }
}
