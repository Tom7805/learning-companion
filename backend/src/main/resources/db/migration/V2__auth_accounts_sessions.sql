-- Tài khoản, đường dẫn xác thực thư điện tử và phiên đăng nhập (QTN-01, QTN-02).

CREATE TABLE user_accounts (
    id                UUID PRIMARY KEY,
    email             VARCHAR(254) NOT NULL,
    display_name      VARCHAR(50)  NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    role              VARCHAR(20)  NOT NULL,
    status            VARCHAR(30)  NOT NULL,
    email_verified_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    -- Ứng dụng luôn lưu địa chỉ thư dạng chữ thường nên ràng buộc duy nhất không phân biệt hoa thường.
    CONSTRAINT uq_user_accounts_email UNIQUE (email),
    CONSTRAINT ck_user_accounts_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_user_accounts_role CHECK (role IN ('LEARNER', 'ADMIN')),
    CONSTRAINT ck_user_accounts_status
        CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'PENDING_DELETION'))
);

-- Chỉ lưu băm SHA-256 của đường dẫn, đường dẫn gốc chỉ nằm trong thư.
CREATE TABLE verification_tokens (
    id             UUID PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    token_hash     VARCHAR(64) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    used_at        TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    CONSTRAINT uq_verification_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_verification_tokens_user ON verification_tokens (user_id, created_at DESC);

CREATE TABLE user_sessions (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    token_hash      VARCHAR(64)  NOT NULL,
    remember_device BOOLEAN      NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    last_active_at  TIMESTAMPTZ  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    user_agent      VARCHAR(512),
    ip_address      VARCHAR(64),
    revoked_at      TIMESTAMPTZ,
    revoke_reason   VARCHAR(40),
    CONSTRAINT uq_user_sessions_hash UNIQUE (token_hash)
);

CREATE INDEX ix_user_sessions_user ON user_sessions (user_id) WHERE revoked_at IS NULL;
