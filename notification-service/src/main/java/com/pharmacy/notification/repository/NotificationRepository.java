package com.pharmacy.notification.repository;

import com.pharmacy.notification.document.NotificationDocument;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class NotificationRepository
        implements PanacheMongoRepository<NotificationDocument> {

    public List<NotificationDocument> findByUserId(String userId) {
        return find("userId", userId).list();
    }

    public List<NotificationDocument> findLatestByUserId(String userId) {
        return find("userId", Sort.descending("createdAt"), userId).list();
    }

    public long countUnreadByUserId(String userId) {
        return count("userId = ?1 and isRead = false", userId);
    }

    /**
     * Tìm thông báo theo ID sự kiện Kafka để chống tạo trùng.
     */
    public Optional<NotificationDocument> findByEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return Optional.empty();
        }

        return find("eventId", eventId).firstResultOptional();
    }
}