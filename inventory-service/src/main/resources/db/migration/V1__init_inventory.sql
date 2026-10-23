-- ==============================================================================
-- Migration V1: Khởi tạo các bảng cho inventory-service (PostgreSQL inventory_db)
-- Tuân thủ ERD và quy ước kiểu dữ liệu trong docs/API-CONTRACT.md & GEMINI.md
-- ==============================================================================

-- Bảng 1: Quản lý từng lô thuốc nhập về kho
CREATE TABLE IF NOT EXISTS batches (
    id UUID PRIMARY KEY,
    medicine_id VARCHAR(24) NOT NULL,
    batch_number VARCHAR(100) NOT NULL,
    expiry_date DATE NOT NULL,
    quantity_in INT NOT NULL CHECK (quantity_in >= 0),
    quantity_available INT NOT NULL CHECK (quantity_available >= 0),
    cost_price NUMERIC(19,2) NOT NULL CHECK (cost_price >= 0),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_batches_medicine_batch UNIQUE (medicine_id, batch_number)
);

CREATE INDEX IF NOT EXISTS idx_batches_medicine_id ON batches(medicine_id);
CREATE INDEX IF NOT EXISTS idx_batches_expiry_date ON batches(expiry_date);
CREATE INDEX IF NOT EXISTS idx_batches_quantity_available ON batches(quantity_available);

-- Bảng 2: Quản lý việc giữ hàng cho từng đơn hàng (FEFO Reservation)
CREATE TABLE IF NOT EXISTS stock_reservations (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    medicine_id VARCHAR(24) NOT NULL,
    batch_id UUID NOT NULL REFERENCES batches(id) ON DELETE CASCADE,
    quantity INT NOT NULL CHECK (quantity > 0),
    status VARCHAR(30) NOT NULL CHECK (status IN ('RESERVED', 'COMMITTED', 'RELEASED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_reservations_order_med_batch UNIQUE (order_id, medicine_id, batch_id)
);

CREATE INDEX IF NOT EXISTS idx_stock_reservations_order_id ON stock_reservations(order_id);
CREATE INDEX IF NOT EXISTS idx_stock_reservations_status ON stock_reservations(status);

-- Bảng 3: Sổ cái biến động kho (Audit log: Nhập, Xuất, Giữ, Nhả, Điều chỉnh)
CREATE TABLE IF NOT EXISTS stock_movements (
    id UUID PRIMARY KEY,
    medicine_id VARCHAR(24) NOT NULL,
    batch_id UUID NOT NULL REFERENCES batches(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL CHECK (type IN ('IMPORT', 'EXPORT', 'RESERVE', 'RELEASE', 'ADJUST')),
    quantity INT NOT NULL,
    order_id UUID,
    reason TEXT,
    created_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_movements_medicine_id ON stock_movements(medicine_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_batch_id ON stock_movements(batch_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_order_id ON stock_movements(order_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_created_at ON stock_movements(created_at);

-- Bảng 4: Bảng Outbox lưu các event cần phát sang Kafka (Transactional Outbox Pattern)
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    message_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_outbox_events_status_created ON outbox_events(status, created_at);

-- Bảng 5: Quản lý các event Kafka đã xử lý để đảm bảo Idempotent
CREATE TABLE IF NOT EXISTS processed_events (
    id UUID PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    order_id UUID,
    result_status VARCHAR(50),
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_processed_events_order_id ON processed_events(order_id);

-- Bảng 6: Nhật ký cảnh báo hạn và tồn kho hằng ngày (Chống phát lặp lại trong ngày)
CREATE TABLE IF NOT EXISTS stock_alerts (
    id UUID PRIMARY KEY,
    alert_type VARCHAR(30) NOT NULL CHECK (alert_type IN ('LOW_STOCK', 'NEAR_EXPIRY')),
    medicine_id VARCHAR(24) NOT NULL,
    batch_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    alert_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_stock_alerts UNIQUE (alert_type, medicine_id, batch_id, alert_date)
);

CREATE INDEX IF NOT EXISTS idx_stock_alerts_date ON stock_alerts(alert_date);

