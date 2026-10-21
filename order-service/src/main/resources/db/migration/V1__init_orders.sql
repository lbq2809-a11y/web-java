-- ==============================================================================
-- Migration V1: Khởi tạo các bảng cho order-service (PostgreSQL order_db)
-- Tuân thủ ERD và quy ước kiểu dữ liệu trong docs/API-CONTRACT.md & GEMINI.md
-- ==============================================================================

-- Bảng 1: Quản lý thông tin đơn hàng
CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    customer_name VARCHAR(255),
    customer_email VARCHAR(255),
    prescription_id UUID,
    status VARCHAR(30) NOT NULL DEFAULT 'CHO_XU_LY' CHECK (status IN ('CHO_XU_LY', 'DANG_GIAO', 'HOAN_THANH', 'DA_HUY', 'HOAN_TRA')),
    total_amount NUMERIC(19,2) NOT NULL CHECK (total_amount >= 0),
    idempotency_key VARCHAR(100) UNIQUE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at);
CREATE INDEX IF NOT EXISTS idx_orders_idempotency_key ON orders(idempotency_key);

-- Bảng 2: Địa chỉ nhận hàng gắn liền với từng đơn hàng (1-1)
CREATE TABLE IF NOT EXISTS shipping_addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    recipient_name VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    address_line VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_shipping_addresses_order_id ON shipping_addresses(order_id);

-- Bảng 3: Chi tiết các mặt hàng trong đơn (Lưu SNAPSHOT tên, giá, loại ETC/OTC tại thời điểm mua)
CREATE TABLE IF NOT EXISTS order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    medicine_id VARCHAR(24) NOT NULL, -- MongoDB ObjectId hex string của medicine-service
    medicine_code VARCHAR(50),
    medicine_name VARCHAR(255) NOT NULL,
    unit VARCHAR(50),
    prescription_type VARCHAR(10) NOT NULL CHECK (prescription_type IN ('OTC', 'ETC')),
    unit_price NUMERIC(19,2) NOT NULL CHECK (unit_price >= 0),
    quantity INT NOT NULL CHECK (quantity > 0),
    subtotal NUMERIC(19,2) NOT NULL CHECK (subtotal >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_medicine_id ON order_items(medicine_id);

-- Bảng 4: Lịch sử thay đổi trạng thái đơn hàng (Audit log)
CREATE TABLE IF NOT EXISTS order_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    old_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    changed_by UUID,
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_order_status_history_order_id ON order_status_history(order_id);

