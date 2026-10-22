-- Khởi tạo bảng users cho auth-service (PostgreSQL auth_db)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    role VARCHAR(50) NOT NULL DEFAULT 'KHACH_HANG',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Seed sẵn 3 tài khoản mặc định (mật khẩu 123456)
INSERT INTO users (id, email, password_hash, full_name, phone, role, active, created_at, updated_at)
VALUES
    ('11111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'khach_hang@pharmacy.local', '$2a$10$eACCYoNOHEqgkHgDE9g2.uS17m3nQy0s9rQ/VwNqfN4Yx3v8u2iGq', 'Nguyễn Văn A', '0901234567', 'KHACH_HANG', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('22222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'duoc_si@pharmacy.local', '$2a$10$eACCYoNOHEqgkHgDE9g2.uS17m3nQy0s9rQ/VwNqfN4Yx3v8u2iGq', 'DS. Trần Thị Dược', '0902345678', 'DUOC_SI', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000000', 'admin@pharmacy.local', '$2a$10$eACCYoNOHEqgkHgDE9g2.uS17m3nQy0s9rQ/VwNqfN4Yx3v8u2iGq', 'Quản trị viên Admin', '0909999999', 'ADMIN', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (email) DO NOTHING;
