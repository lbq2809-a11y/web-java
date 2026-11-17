
package com.pharmacy.notification.document;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;

/**
 * Document quản lý các mẫu email của hệ thống.
 *
 * Database: notification_db
 * Collection: templates
 */
@MongoEntity(collection = "templates")
public class TemplateDocument extends PanacheMongoEntity {

    /**
     * Mã định danh mẫu email.
     * Ví dụ:
     * ORDER_CONFIRMATION
     * ORDER_STATUS
     * PASSWORD_RESET
     * PAYMENT_RECEIPT
     */
    public String templateKey;

    /**
     * Tên mẫu email.
     */
    public String name;

    /**
     * Tiêu đề email.
     */
    public String subject;

    /**
     * Nội dung HTML của mẫu email.
     * Có thể sử dụng biến Qute để thay thế dữ liệu.
     */
    public String htmlContent;

    /**
     * Mô tả mục đích sử dụng mẫu email.
     */
    public String description;

    /**
     * Trạng thái hoạt động của mẫu.
     */
    public boolean active = true;

    /**
     * Thời gian tạo.
     */
    public Instant createdAt = Instant.now();

    /**
     * Thời gian cập nhật gần nhất.
     */
    public Instant updatedAt = Instant.now();
}
