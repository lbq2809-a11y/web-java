
package com.pharmacy.notification.document;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;

/**
 * Lưu lịch sử gửi email của hệ thống.
 *
 * Database: notification_db
 * Collection: email_logs
 */
@MongoEntity(collection = "email_logs")
public class EmailLogDocument extends PanacheMongoEntity {
    /**
     * ID sự kiện Kafka liên quan đến email.
     * Dùng để hạn chế gửi email trùng khi Kafka phát lại event.
     */
    public String eventId;
    /**
     * Địa chỉ email người nhận.
     */
    public String recipient;

    /**
     * Tên template email được sử dụng.
     * Ví dụ: order-confirmation,
     * password-reset, order-status.
     */
    public String template;

    /**
     * Trạng thái gửi email.
     * PENDING: Đang chờ gửi.
     * SENT: Gửi thành công.
     * FAILED: Gửi thất bại.
     */
    public String status = "PENDING";

    /**
     * Nội dung lỗi nếu gửi email thất bại.
     */
    public String error;

    /**
     * Số lần thực hiện gửi email.
     */
    public int attempts = 0;

    /**
     * Thời điểm tạo bản ghi.
     */
    public Instant createdAt = Instant.now();

    /**
     * Thời điểm gửi email thành công.
     */
    public Instant sentAt;
}
