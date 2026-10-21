-- ==============================================================================
-- Migration V1: Khởi tạo bảng cho prescription-service (PostgreSQL prescription_db)
-- Tuân thủ ERD và quy ước kiểu dữ liệu trong docs/API-CONTRACT.md & GEMINI.md
-- ==============================================================================

-- Bảng 1: Quản lý thông tin đơn thuốc do khách hàng tải lên và quá trình duyệt
CREATE TABLE IF NOT EXISTS prescriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    customer_name VARCHAR(255),
    customer_email VARCHAR(255),
    image_path VARCHAR(500) NOT NULL,
    image_content_type VARCHAR(50) NOT NULL,
    image_size_bytes BIGINT NOT NULL,
    notes TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'CHO_DUYET' CHECK (status IN ('CHO_DUYET', 'DA_DUYET', 'TU_CHOI')),
    pharmacist_id UUID,
    pharmacist_name VARCHAR(255),
    rejection_reason TEXT,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_prescriptions_customer_id ON prescriptions(customer_id);
CREATE INDEX IF NOT EXISTS idx_prescriptions_status ON prescriptions(status);
CREATE INDEX IF NOT EXISTS idx_prescriptions_created_at ON prescriptions(created_at);

-- Bảng 2: Danh sách các thuốc được dược sĩ duyệt trong đơn và số lượng tối đa được phép mua
CREATE TABLE IF NOT EXISTS prescription_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id UUID NOT NULL REFERENCES prescriptions(id) ON DELETE CASCADE,
    medicine_id VARCHAR(24) NOT NULL, -- MongoDB ObjectId hex string của medicine-service
    medicine_name VARCHAR(255),
    max_allowed_quantity INT NOT NULL CHECK (max_allowed_quantity > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_prescription_item UNIQUE (prescription_id, medicine_id)
);

CREATE INDEX IF NOT EXISTS idx_prescription_items_prescription_id ON prescription_items(prescription_id);
CREATE INDEX IF NOT EXISTS idx_prescription_items_medicine_id ON prescription_items(medicine_id);

-- Bảng 3: Liên kết giữa đơn thuốc đã duyệt và đơn hàng (đảm bảo 1 đơn thuốc gắn với 1 đơn hàng)
CREATE TABLE IF NOT EXISTS prescription_order_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id UUID NOT NULL REFERENCES prescriptions(id) ON DELETE RESTRICT,
    order_id UUID NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    linked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_prescription_order_links_order_id ON prescription_order_links(order_id);
CREATE INDEX IF NOT EXISTS idx_prescription_order_links_prescription_id ON prescription_order_links(prescription_id);
CREATE INDEX IF NOT EXISTS idx_prescription_order_links_customer_id ON prescription_order_links(customer_id);

