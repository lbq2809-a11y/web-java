
package com.pharmacy.notification.document;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;

/**
 * Document lưu thông báo của người dùng.
 *
 * MongoDB database: notification_db
 * Collection: notifications
 */
@MongoEntity(collection = "notifications")
public class NotificationDocument extends PanacheMongoEntity {
    /**
     * ID duy nhất của sự kiện Kafka đã tạo thông báo.
     * Dùng để tránh tạo thông báo trùng khi Kafka gửi lại sự kiện.
     */
    public String eventId;
    /**
     * UUID của người dùng nhận thông báo.
     * Lấy từ JWT claim sub.
     */
    public String userId;

    /**
     * Loại thông báo.
     * Ví dụ: ORDER_CREATED, PAYMENT_COMPLETED,
     * ORDER_STATUS_CHANGED, PRESCRIPTION_REVIEWED.
     */
    public String type;

    /**
     * Tiêu đề thông báo.
     */
    public String title;

    /**
     * Nội dung chi tiết thông báo.
     */
    public String content;

    /**
     * Trạng thái đã đọc.
     * false: Chưa đọc.
     * true: Đã đọc.
     */
    public boolean isRead = false;

    /**
     * Thời điểm tạo thông báo.
     */
    public Instant createdAt = Instant.now();
}
