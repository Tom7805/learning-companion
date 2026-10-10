-- Đăng nhập, khóa tạm khi nhập sai và thông tin thiết bị của phiên (NCL-01-CN-002, QTN-02).

-- Thiết bị nhận diện bằng cookie riêng của trình duyệt (chỉ lưu băm), kèm mô tả để hiển thị trong danh sách.
ALTER TABLE user_sessions
    ADD COLUMN device_id_hash   VARCHAR(64),
    ADD COLUMN browser          VARCHAR(40),
    ADD COLUMN operating_system VARCHAR(40),
    ADD COLUMN device_type      VARCHAR(20),
    ADD COLUMN location         VARCHAR(120),
    ADD COLUMN local_network    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN revoke_link_hash VARCHAR(64);

-- Đường dẫn "đăng xuất thiết bị đó" trong thư cảnh báo thiết bị mới.
CREATE UNIQUE INDEX uq_user_sessions_revoke_link ON user_sessions (revoke_link_hash)
    WHERE revoke_link_hash IS NOT NULL;
CREATE INDEX ix_user_sessions_device ON user_sessions (user_id, device_id_hash);

-- Đếm số lần nhập sai liên tiếp theo địa chỉ thư, kể cả địa chỉ chưa đăng ký,
-- để phản hồi khóa tạm không tiết lộ địa chỉ nào có tài khoản.
CREATE TABLE login_throttles (
    email          VARCHAR(254) PRIMARY KEY,
    failed_count   INT          NOT NULL,
    locked_until   TIMESTAMPTZ,
    last_failed_at TIMESTAMPTZ  NOT NULL
);
