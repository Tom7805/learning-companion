-- Đăng nhập bằng tài khoản của nhà cung cấp định danh bên ngoài (NCL-01-CN-007).

-- Tài khoản tạo qua nhà cung cấp định danh chưa có mật khẩu cho tới khi người học tự đặt.
ALTER TABLE user_accounts ALTER COLUMN password_hash DROP NOT NULL;

-- Tài khoản bên ngoài đã liên kết. Mỗi tài khoản bên ngoài chỉ gắn với một tài khoản người học,
-- mỗi người học có tối đa một tài khoản cho từng nhà cung cấp.
CREATE TABLE external_identities (
    id           UUID PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    provider     VARCHAR(20)  NOT NULL,
    subject      VARCHAR(255) NOT NULL,
    email        VARCHAR(254) NOT NULL,
    linked_at    TIMESTAMPTZ  NOT NULL,
    last_used_at TIMESTAMPTZ,
    CONSTRAINT uq_external_identities_subject UNIQUE (provider, subject),
    CONSTRAINT uq_external_identities_user UNIQUE (user_id, provider)
);

-- Một lượt ủy quyền đang diễn ra: mã chống giả mạo (state) chỉ lưu băm, gắn với trình duyệt đã bắt đầu
-- lượt đó qua cookie riêng, dùng một lần và hết hạn sau mười phút.
CREATE TABLE oauth_flows (
    id               UUID PRIMARY KEY,
    state_hash       VARCHAR(64)  NOT NULL,
    browser_hash     VARCHAR(64)  NOT NULL,
    provider         VARCHAR(20)  NOT NULL,
    mode             VARCHAR(10)  NOT NULL,
    user_id          UUID REFERENCES user_accounts (id) ON DELETE CASCADE,
    code_verifier    VARCHAR(128) NOT NULL,
    remember_device  BOOLEAN      NOT NULL DEFAULT FALSE,
    return_to        VARCHAR(200),
    created_at       TIMESTAMPTZ  NOT NULL,
    expires_at       TIMESTAMPTZ  NOT NULL,
    consumed_at      TIMESTAMPTZ,
    CONSTRAINT uq_oauth_flows_state UNIQUE (state_hash),
    CONSTRAINT ck_oauth_flows_mode CHECK (mode IN ('LOGIN', 'LINK'))
);

-- Bước còn dang dở sau khi nhà cung cấp xác nhận: đồng ý điều khoản (người mới) hoặc nhập mật khẩu để
-- liên kết (địa chỉ thư đã có tài khoản). Chỉ lưu băm của mã tiếp tục.
CREATE TABLE oauth_pending (
    id              UUID PRIMARY KEY,
    token_hash      VARCHAR(64)  NOT NULL,
    kind            VARCHAR(10)  NOT NULL,
    provider        VARCHAR(20)  NOT NULL,
    subject         VARCHAR(255) NOT NULL,
    email           VARCHAR(254) NOT NULL,
    display_name    VARCHAR(100),
    remember_device BOOLEAN      NOT NULL DEFAULT FALSE,
    return_to       VARCHAR(200),
    created_at      TIMESTAMPTZ  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    consumed_at     TIMESTAMPTZ,
    CONSTRAINT uq_oauth_pending_token UNIQUE (token_hash),
    CONSTRAINT ck_oauth_pending_kind CHECK (kind IN ('SIGNUP', 'LINK'))
);
