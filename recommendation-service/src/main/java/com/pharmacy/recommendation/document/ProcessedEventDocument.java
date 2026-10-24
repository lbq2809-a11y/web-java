package com.pharmacy.recommendation.document;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;

/**
 * Lưu vết eventId của các sự kiện Kafka đã xử lý nhằm đảm bảo tính Idempotent (chống xử lý trùng lặp).
 */
@MongoEntity(collection = "processed_events")
public class ProcessedEventDocument extends PanacheMongoEntity {

    /** Mã định danh duy nhất của event (UUID dạng chuỗi) */
    public String eventId;

    /** Loại sự kiện (order.created, order.status-changed) */
    public String eventType;

    /** Thời điểm xử lý */
    public Instant processedAt;
}

