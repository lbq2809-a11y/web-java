package com.pharmacy.consultation.repository;

import com.pharmacy.consultation.entity.ConversationDocument;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

/**
 * Kho lưu trữ các phiên hội thoại tư vấn trong MongoDB.
 */
@ApplicationScoped
public class ConversationRepository implements PanacheMongoRepository<ConversationDocument> {

    public Optional<ConversationDocument> findByConversationId(String conversationId) {
        return find("conversationId", conversationId).firstResultOptional();
    }

    public List<ConversationDocument> findByUserIdOrderByUpdatedAtDesc(String userId) {
        return list("userId", Sort.by("updatedAt").descending(), userId);
    }

    public long deleteByConversationId(String conversationId) {
        return delete("conversationId", conversationId);
    }
}

