package com.pharmacy.notification.service;

import com.pharmacy.notification.document.NotificationDocument;
import com.pharmacy.notification.repository.NotificationRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
public class NotificationService {

    @Inject
    NotificationRepository notificationRepository;

    /**
     * Tạo thông báo mới.
     * Kiểm tra eventId trước để tránh tạo thông báo trùng.
     */
    public NotificationDocument createNotification(
            String eventId,
            String userId,
            String type,
            String title,
            String content
    ) {
        validateRequired(userId, "userId");
        validateRequired(type, "type");
        validateRequired(title, "title");
        validateRequired(content, "content");

        // Kiểm tra sự kiện Kafka đã được xử lý hay chưa.
        if (eventId != null && !eventId.isBlank()) {
            Optional<NotificationDocument> existing =
                    notificationRepository.findByEventId(eventId);

            if (existing.isPresent()) {
                return existing.get();
            }
        }

        NotificationDocument notification = new NotificationDocument();

        notification.eventId = eventId;
        notification.userId = userId;
        notification.type = type;
        notification.title = title;
        notification.content = content;
        notification.isRead = false;
        notification.createdAt = Instant.now();

        notificationRepository.persist(notification);

        return notification;
    }

    /**
     * Lấy danh sách thông báo mới nhất của người dùng.
     */
    public List<NotificationDocument> getMyNotifications(String userId) {
        validateRequired(userId, "userId");

        return notificationRepository.findLatestByUserId(userId);
    }

    /**
     * Đếm số thông báo chưa đọc.
     */
    public long countUnread(String userId) {
        validateRequired(userId, "userId");

        return notificationRepository.countUnreadByUserId(userId);
    }

    /**
     * Đánh dấu một thông báo đã đọc.
     * Chỉ chủ sở hữu mới được phép cập nhật.
     */
    public Optional<NotificationDocument> markAsRead(
            String notificationId,
            String userId
    ) {
        validateRequired(userId, "userId");

        if (notificationId == null || !ObjectId.isValid(notificationId)) {
            return Optional.empty();
        }

        NotificationDocument notification =
                notificationRepository.findById(new ObjectId(notificationId));

        if (notification == null) {
            return Optional.empty();
        }

        // Không cho phép người dùng cập nhật thông báo của người khác.
        if (!Objects.equals(notification.userId, userId)) {
            return Optional.empty();
        }

        if (!notification.isRead) {
            notification.isRead = true;
            notificationRepository.update(notification);
        }

        return Optional.of(notification);
    }

    /**
     * Đánh dấu tất cả thông báo chưa đọc thành đã đọc.
     *
     * @return số thông báo được cập nhật
     */
    public long markAllAsRead(String userId) {
        validateRequired(userId, "userId");

        List<NotificationDocument> unreadNotifications =
                notificationRepository
                        .find("userId = ?1 and isRead = ?2", userId, false)
                        .list();

        for (NotificationDocument notification : unreadNotifications) {
            notification.isRead = true;
            notificationRepository.update(notification);
        }

        return unreadNotifications.size();
    }

    private void validateRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " không được để trống"
            );
        }
    }
}