package com.pharmacy.consultation.entity;

import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Phiên hội thoại của người dùng với trợ lý AI, lưu trữ trong MongoDB.
 */
@MongoEntity(collection = "conversations")
public class ConversationDocument {

    public ObjectId id;
    public String conversationId; // UUID
    public String userId;         // UUID người dùng
    public String title;          // Tiêu đề tóm tắt (câu hỏi đầu tiên)
    public List<MessageItem> messages = new ArrayList<>();
    public Instant createdAt;
    public Instant updatedAt;

    public ConversationDocument() {
    }

    public ConversationDocument(String conversationId, String userId, String title, Instant createdAt, Instant updatedAt) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}

