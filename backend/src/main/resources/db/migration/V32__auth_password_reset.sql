-- Quên mật khẩu và đổi mật khẩu (NCL-01-CN-003, QTN-02).

-- Đường dẫn đặt lại mật khẩu dùng một lần, chỉ lưu băm của chuỗi bí mật.
CREATE TABLE password_reset_tokens (
    id             UUID PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    token_hash     VARCHAR(64) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    used_at        TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id, created_at DESC);

-- Thời điểm đổi mật khẩu gần nhất, hiển thị trong mục bảo mật tài khoản.
ALTER TABLE user_accounts ADD COLUMN password_changed_at TIMESTAMPTZ;
