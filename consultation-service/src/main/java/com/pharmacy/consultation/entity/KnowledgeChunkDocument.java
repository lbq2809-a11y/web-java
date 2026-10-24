package com.pharmacy.consultation.entity;

import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.List;

/**
 * Đoạn tri thức (chunk) được chuẩn hóa từ thông tin thuốc của medicine-service.
 */
@MongoEntity(collection = "knowledge_chunks")
public class KnowledgeChunkDocument {

    public ObjectId id;
    public String medicineId;
    public String medicineName;
    public String section;
    public String text;
    public List<Double> embedding;
    public String contentHash;
    public Instant updatedAt;

    public KnowledgeChunkDocument() {
    }

    public KnowledgeChunkDocument(String medicineId, String medicineName, String section, String text, String contentHash, Instant updatedAt) {
        this.medicineId = medicineId;
        this.medicineName = medicineName;
        this.section = section;
        this.text = text;
        this.contentHash = contentHash;
        this.updatedAt = updatedAt;
    }
}

