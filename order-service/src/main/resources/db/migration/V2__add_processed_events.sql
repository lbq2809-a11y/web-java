-- ==============================================================================
-- Migration V2: Bảng ghi nhận các sự kiện Kafka đã xử lý (Chống xử lý trùng lặp - Idempotent Consumer)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_processed_events_type ON processed_events(event_type);

