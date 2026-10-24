package com.pharmacy.consultation.service;

import com.pharmacy.consultation.client.EmbeddingClient;
import com.pharmacy.consultation.client.MedicineServiceClient;
import com.pharmacy.consultation.dto.MedicineResponse;
import com.pharmacy.consultation.dto.PagedResponse;
import com.pharmacy.consultation.entity.KnowledgeChunkDocument;
import com.pharmacy.consultation.repository.KnowledgeChunkRepository;
import com.pharmacy.consultation.repository.RagCacheRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Nạp tri thức thuốc từ medicine-service vào MongoDB (collection knowledge_chunks),
 * tạo vector nhúng (Embedding), lưu vào RAM (MedicineKnowledgeCache & VectorKnowledgeStore)
 * và quản lý vô hiệu hóa cache RAG.
 */
@ApplicationScoped
public class KnowledgeIngestionService {

    private static final Logger LOG = Logger.getLogger(KnowledgeIngestionService.class);
    private static final int PAGE_SIZE = 100;

    @Inject
    @RestClient
    MedicineServiceClient medicineServiceClient;

    @Inject
    KnowledgeChunkRepository chunkRepository;

    @Inject
    MedicineKnowledgeCache medicineCache;

    @Inject
    VectorKnowledgeStore vectorStore;

    @Inject
    EmbeddingClient embeddingClient;

    @Inject
    RagCacheRepository ragCacheRepository;

    @Inject
    TextNormalizer textNormalizer;

    void onStart(@Observes StartupEvent ev) {
        // Tự động đồng bộ tri thức khi khởi động service
        try {
            ingestAllMedicines();
        } catch (Exception e) {
            LOG.warnf("Chưa thể đồng bộ thuốc từ medicine-service lúc khởi động: %s. Service vẫn hoạt động bình thường.", e.getMessage());
        }
    }

    public synchronized ReindexResult reindex() {
        return ingestAllMedicines();
    }

    public record ReindexResult(int totalMedicines, int totalChunks) {}

    private ReindexResult ingestAllMedicines() {
        LOG.info("Bắt đầu đồng bộ danh mục thuốc từ medicine-service...");
        List<CachedMedicine> cachedList = new ArrayList<>();
        int page = 0;
        int totalMedicines = 0;
        int totalChunks = 0;

        while (true) {
            PagedResponse<MedicineResponse> paged;
            try {
                paged = medicineServiceClient.getMedicines(page, PAGE_SIZE, false);
            } catch (Exception e) {
                LOG.errorf("Lỗi khi gọi API medicine-service tại trang %d: %s", page, e.getMessage());
                if (page == 0) {
                    break;
                }
                break;
            }

            if (paged == null || paged.getItems() == null || paged.getItems().isEmpty()) {
                break;
            }

            for (MedicineResponse med : paged.getItems()) {
                if (!med.active()) {
                    continue;
                }

                totalMedicines++;

                // 1. Lưu vào CachedMedicine
                CachedMedicine cm = new CachedMedicine();
                cm.setMedicineId(med.id());
                cm.setName(med.name());
                cm.setNormalizedName(textNormalizer.normalize(med.name()));
                cm.setNoDiacriticsName(textNormalizer.normalizeWithoutDiacritics(med.name()));

                cm.setActiveIngredient(med.activeIngredient());
                cm.setNormalizedActiveIngredient(textNormalizer.normalize(med.activeIngredient()));
                cm.setNoDiacriticsActiveIngredient(textNormalizer.normalizeWithoutDiacritics(med.activeIngredient()));

                cm.setPrescriptionType(med.prescriptionType());
                cm.setUnit(med.unit());
                cm.setPrice(med.price());
                cm.setIndications(med.indications());
                cm.setContraindications(med.contraindications());
                cm.setDosage(med.dosage());
                cachedList.add(cm);

                // 2. Chia chunk tri thức lưu vào MongoDB
                totalChunks += saveOrUpdateChunk(med.id(), med.name(), "indications", med.indications());
                totalChunks += saveOrUpdateChunk(med.id(), med.name(), "contraindications", med.contraindications());
                totalChunks += saveOrUpdateChunk(med.id(), med.name(), "dosage", med.dosage());
                totalChunks += saveOrUpdateChunk(med.id(), med.name(), "composition", med.activeIngredient());
                totalChunks += saveOrUpdateChunk(med.id(), med.name(), "general",
                        String.format("%s - %s - Giá: %s/%s - Nhóm: %s",
                                med.name(), med.activeIngredient(), med.price(), med.unit(), med.prescriptionType()));
            }

            if (paged.getItems().size() < PAGE_SIZE) {
                break;
            }
            page++;
        }

        medicineCache.update(cachedList);

        // 3. Tính toán và cập nhật Vector Embeddings cho các chunk
        generateAndPersistEmbeddings();

        // 4. Xóa toàn bộ RAG cache câu trả lời khi reindex (theo yêu cầu mục C.4)
        try {
            long deletedCache = ragCacheRepository.deleteAll();
            LOG.infof("Đã xóa %d bản ghi cache RAG cũ sau khi reindex.", deletedCache);
        } catch (Exception e) {
            LOG.warnf("Không thể xóa RAG cache: %s", e.getMessage());
        }

        LOG.infof("Đồng bộ hoàn tất: %d thuốc, %d chunks tri thức.", totalMedicines, totalChunks);
        return new ReindexResult(totalMedicines, totalChunks);
    }

    private int saveOrUpdateChunk(String medicineId, String medicineName, String section, String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        String hash = computeSha256(text);
        Instant now = Instant.now();

        Optional<KnowledgeChunkDocument> existingOpt = chunkRepository.findByMedicineIdAndSection(medicineId, section);
        if (existingOpt.isPresent()) {
            KnowledgeChunkDocument existing = existingOpt.get();
            if (!hash.equals(existing.contentHash)) {
                existing.text = text;
                existing.contentHash = hash;
                existing.embedding = null; // Cần tính lại vector nhúng vì nội dung đã đổi
                existing.updatedAt = now;
                chunkRepository.update(existing);

                // Xóa cache RAG liên quan đến thuốc này
                ragCacheRepository.deleteByMedicineId(medicineId);
            }
        } else {
            KnowledgeChunkDocument newChunk = new KnowledgeChunkDocument(
                    medicineId, medicineName, section, text, hash, now
            );
            chunkRepository.persist(newChunk);
        }
        return 1;
    }

    private void generateAndPersistEmbeddings() {
        try {
            List<KnowledgeChunkDocument> allChunks = chunkRepository.listAll();
            List<KnowledgeChunkDocument> chunksNeedingEmbedding = new ArrayList<>();

            for (KnowledgeChunkDocument c : allChunks) {
                if (c.embedding == null || c.embedding.isEmpty()) {
                    chunksNeedingEmbedding.add(c);
                }
            }

            if (!chunksNeedingEmbedding.isEmpty()) {
                LOG.infof("Đang sinh vector embedding cho %d chunks...", chunksNeedingEmbedding.size());
                List<String> texts = chunksNeedingEmbedding.stream()
                        .map(c -> String.format("%s - %s: %s", c.medicineName, c.section, c.text))
                        .toList();

                List<List<Double>> vectors = embeddingClient.embedBatch(texts);
                if (vectors != null && vectors.size() == chunksNeedingEmbedding.size()) {
                    for (int i = 0; i < chunksNeedingEmbedding.size(); i++) {
                        KnowledgeChunkDocument doc = chunksNeedingEmbedding.get(i);
                        doc.embedding = vectors.get(i);
                        doc.updatedAt = Instant.now();
                        chunkRepository.update(doc);
                    }
                }
            }

            // Nạp lại toàn bộ vector vào RAM cho VectorKnowledgeStore
            List<KnowledgeChunkDocument> updatedChunks = chunkRepository.listAll();
            List<VectorItem> ramVectors = new ArrayList<>();
            for (KnowledgeChunkDocument c : updatedChunks) {
                if (c.embedding != null && !c.embedding.isEmpty()) {
                    ramVectors.add(new VectorItem(
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
            vectorStore.updateVectors(ramVectors);

        } catch (Exception e) {
            LOG.warnf("Chưa thể sinh vector embedding lúc này (dịch vụ AI chưa sẵn sàng): %s", e.getMessage());
        }
    }

    private String computeSha256(String input) {
        if (input == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
