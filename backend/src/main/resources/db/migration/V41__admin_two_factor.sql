-- Tài khoản quản trị viên duy nhất có xác thực hai bước (NCL-03-CN-002, QTN-01, QTN-02).

-- Dữ liệu cũ có thể có nhiều quản trị viên (công cụ thử nghiệm cấp vai trò tùy ý): giữ người được tạo sớm nhất.
UPDATE user_accounts
SET role = 'LEARNER'
WHERE role = 'ADMIN'
  AND id <> (SELECT id FROM user_accounts WHERE role = 'ADMIN' ORDER BY created_at, id LIMIT 1);

-- Cả hệ thống chỉ có một quản trị viên: chỉ mục duy nhất trên một biểu thức hằng của các dòng ADMIN.
CREATE UNIQUE INDEX uq_user_accounts_single_admin ON user_accounts ((role)) WHERE role = 'ADMIN';

-- Khóa bí mật của ứng dụng sinh mã (TOTP), mã hóa AES-GCM. last_used_step chặn dùng lại một mã đã dùng.
-- reset_required: quản trị viên vừa vào bằng mã khôi phục, lần đăng nhập kế tiếp phải thiết lập lại.
CREATE TABLE admin_totp_secrets (
    account_id        UUID PRIMARY KEY REFERENCES user_accounts (id) ON DELETE CASCADE,
    secret_ciphertext BYTEA       NOT NULL,
    enrolled_at       TIMESTAMPTZ NOT NULL,
    last_used_step    BIGINT      NOT NULL DEFAULT 0,
    reset_required    BOOLEAN     NOT NULL DEFAULT FALSE
);

-- Mười mã khôi phục dùng một lần, chỉ lưu băm SHA-256.
CREATE TABLE admin_recovery_codes (
    id         UUID PRIMARY KEY,
    account_id UUID        NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    CONSTRAINT uq_admin_recovery_codes_hash UNIQUE (account_id, code_hash)
);

-- Bước thứ hai của một lần đăng nhập: mật khẩu đã đúng, chờ mã sáu chữ số hoặc thiết lập ứng dụng sinh mã.
CREATE TABLE admin_login_challenges (
    id                        UUID PRIMARY KEY,
    account_id                UUID        NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    token_hash                VARCHAR(64) NOT NULL,
    stage                     VARCHAR(20) NOT NULL,
    pending_secret_ciphertext BYTEA,
    created_at                TIMESTAMPTZ NOT NULL,
    expires_at                TIMESTAMPTZ NOT NULL,
    completed_at              TIMESTAMPTZ,
    CONSTRAINT uq_admin_login_challenges_hash UNIQUE (token_hash),
    CONSTRAINT ck_admin_login_challenges_stage CHECK (stage IN ('VERIFY', 'ENROLL'))
);

CREATE INDEX ix_admin_login_challenges_account ON admin_login_challenges (account_id, created_at DESC);

-- Phiên quản trị: hết hạn sau tám giờ kể từ lúc đăng nhập dù vẫn thao tác; chỉ phiên đã qua bước hai mới mang quyền
-- quản trị (phiên mở trước khi tài khoản thành quản trị viên không dùng được nữa).
ALTER TABLE user_sessions
    ADD COLUMN max_expires_at      TIMESTAMPTZ,
    ADD COLUMN two_factor_verified BOOLEAN NOT NULL DEFAULT FALSE;
