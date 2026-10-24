package com.pharmacy.consultation.entity;

import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Bản ghi lưu đệm câu trả lời Tầng 2 (RAG) trong MongoDB để đảm bảo tính nhất quán
 * và giảm thiểu chi phí/độ trễ gọi LLM.
 */
@MongoEntity(collection = "rag_caches")
public class RagCacheDocument {

    public ObjectId id;
    public String cacheKey; // Khóa băm SHA-256 từ intent + sortedMedicineIds + chunksHash
    public String intent;
    public List<String> retrievedMedicineIds = new ArrayList<>();
    public String chunksHash;
    public String answer;
    public List<KnowledgeSource> sources = new ArrayList<>();
    public int tier;
    public Instant createdAt;

    public RagCacheDocument() {
    }

    public RagCacheDocument(String cacheKey, String intent, List<String> retrievedMedicineIds,
                            String chunksHash, String answer, List<KnowledgeSource> sources,
                            int tier, Instant createdAt) {
        this.cacheKey = cacheKey;
        this.intent = intent;
        this.retrievedMedicineIds = retrievedMedicineIds != null ? retrievedMedicineIds : new ArrayList<>();
        this.chunksHash = chunksHash;
        this.answer = answer;
        this.sources = sources != null ? sources : new ArrayList<>();
        this.tier = tier;
        this.createdAt = createdAt;
    }
}

