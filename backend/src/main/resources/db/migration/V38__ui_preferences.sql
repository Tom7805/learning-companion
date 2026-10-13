-- Tùy chọn giao diện theo tài khoản (NCL-02-CN-001): chế độ sáng tối, cỡ chữ và trạng thái thu gọn thanh bên,
-- để người học đổi máy vẫn thấy giao diện như đã chọn. Chưa chọn gì thì không có dòng, giao diện dùng mặc định.
CREATE TABLE ui_preferences (
    id                UUID PRIMARY KEY,
    owner_id          UUID        NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    theme_mode        VARCHAR(10) NOT NULL,
    font_size         VARCHAR(10) NOT NULL,
    sidebar_collapsed BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at        TIMESTAMPTZ NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_ui_preferences_owner UNIQUE (owner_id),
    CONSTRAINT ck_ui_preferences_theme CHECK (theme_mode IN ('LIGHT', 'DARK', 'SYSTEM')),
    CONSTRAINT ck_ui_preferences_font CHECK (font_size IN ('SMALL', 'MEDIUM', 'LARGE'))
);
