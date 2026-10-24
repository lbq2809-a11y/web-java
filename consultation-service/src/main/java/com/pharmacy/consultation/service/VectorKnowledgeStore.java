package com.pharmacy.consultation.service;

import com.pharmacy.consultation.entity.KnowledgeChunkDocument;
import com.pharmacy.consultation.repository.KnowledgeChunkRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Quản lý kho vector nhúng lưu trong RAM:
 * Khi khởi động server, nạp sẵn danh sách vector từ MongoDB (knowledge_chunks) lên RAM,
 * TUYỆT ĐỐI KHÔNG gọi lại API embedding khi khởi động lại server.
 */
@ApplicationScoped
public class VectorKnowledgeStore {

    private static final Logger LOG = Logger.getLogger(VectorKnowledgeStore.class);

    private final List<VectorItem> vectorItems = new CopyOnWriteArrayList<>();

    @Inject
    KnowledgeChunkRepository chunkRepository;

    void onStart(@Observes StartupEvent ev) {
        loadVectorsFromMongo();
    }

    public synchronized void loadVectorsFromMongo() {
        try {
            List<KnowledgeChunkDocument> chunks = chunkRepository.listAll();
            List<VectorItem> loaded = new ArrayList<>();
            for (KnowledgeChunkDocument c : chunks) {
                if (c.embedding != null && !c.embedding.isEmpty()) {
                    loaded.add(new VectorItem(
                            c.id != null ? c.id.toHexString() : null,
                            c.medicineId,
                            c.medicineName,
                            c.section,
                            c.text,
                            c.embedding,
                            c.contentHash
                    ));
                }
            }
            vectorItems.clear();
            vectorItems.addAll(loaded);
            LOG.infof("Đã nạp sẵn %d vector tri thức từ MongoDB lên RAM để tính toán Cosine.", vectorItems.size());
        } catch (Exception e) {
            LOG.warnf("Không thể nạp vector từ MongoDB lúc khởi động: %s", e.getMessage());
        }
    }

    public synchronized void updateVectors(List<VectorItem> items) {
        vectorItems.clear();
        if (items != null) {
            vectorItems.addAll(items);
        }
        LOG.infof("Cập nhật kho vector RAM thành công: %d vector.", vectorItems.size());
    }

    public List<VectorItem> getAll() {
        return new ArrayList<>(vectorItems);
    }

    public int size() {
        return vectorItems.size();
    }
}

