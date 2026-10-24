package com.pharmacy.recommendation.document;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;
import java.util.List;

/**
 * Đơn hàng tạm thời chờ hoàn thành hoặc hủy (Pending Orders).
 * Lưu snapshot danh sách thuốc từ event order.created vì event order.status-changed không chứa items.
 */
@MongoEntity(collection = "pending_orders")
public class PendingOrderDocument extends PanacheMongoEntity {

    /** Mã đơn hàng (UUID dạng chuỗi) */
    public String orderId;

    /** Mã khách hàng (UUID dạng chuỗi) */
    public String userId;

    /** Danh sách medicineId dạng chuỗi hex 24 ký tự */
    public List<String> items;

    /** Trạng thái đã được tính vào thống kê gợi ý hay chưa */
    public boolean processed;

    /** Thời điểm nhận đơn */
    public Instant createdAt;
}

