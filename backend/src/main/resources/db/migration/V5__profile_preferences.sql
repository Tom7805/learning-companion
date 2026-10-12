-- Hồ sơ và tùy chọn học tập cá nhân (NCL-01-CN-005). Tên hiển thị vẫn nằm ở user_accounts.
-- Hồ sơ là dữ liệu của người học: mọi truy vấn đi kèm owner_id (QTN-08).

CREATE TABLE user_profiles (
    id                 UUID PRIMARY KEY,
    owner_id           UUID        NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    -- Mã múi giờ IANA; trống nghĩa là chưa chọn, giao diện dùng múi giờ của thiết bị.
    time_zone          VARCHAR(64),
    assistant_language VARCHAR(8)  NOT NULL DEFAULT 'vi',
    daily_goal_minutes INT         NOT NULL DEFAULT 30,
    day_rollover_hour  INT         NOT NULL DEFAULT 4,
    -- Ảnh đại diện đã cắt vuông, lưu mã hóa trong kho tệp.
    avatar_storage_key VARCHAR(100),
    avatar_updated_at  TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_user_profiles_owner UNIQUE (owner_id),
    CONSTRAINT ck_user_profiles_language CHECK (assistant_language IN ('vi', 'en')),
    CONSTRAINT ck_user_profiles_goal CHECK (daily_goal_minutes BETWEEN 5 AND 600),
    CONSTRAINT ck_user_profiles_rollover CHECK (day_rollover_hour BETWEEN 0 AND 23)
);
