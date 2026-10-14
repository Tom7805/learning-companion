-- Trung tâm thông báo trong ứng dụng (NCL-02-CN-002). Mỗi thông báo thuộc đúng một người học, trỏ tới một đối tượng
-- (loại + mã) để mở đúng chỗ, và được giữ chín mươi ngày. Tiêu đề không lưu sẵn: giao diện dựng từ loại và tham số
-- theo ngôn ngữ người học đang dùng.
CREATE TABLE notifications (
    id          UUID PRIMARY KEY,
    owner_id    UUID         NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    type        VARCHAR(64)  NOT NULL,
    params      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    target_type VARCHAR(64)  NOT NULL,
    target_id   UUID,
    -- Cùng một sự kiện gửi lại (thử lại, chạy lại tác vụ) thì không thành thông báo thứ hai (QTN-10).
    dedup_key   VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    read_at     TIMESTAMPTZ,
    CONSTRAINT uq_notifications_dedup UNIQUE (owner_id, dedup_key)
);

-- Danh sách mới nhất trước, phân trang theo (thời điểm, mã).
CREATE INDEX ix_notifications_owner_recent ON notifications (owner_id, created_at DESC, id DESC);
-- Đếm số chưa đọc cho biểu tượng chuông.
CREATE INDEX ix_notifications_owner_unread ON notifications (owner_id) WHERE read_at IS NULL;
-- Tác vụ dọn thông báo quá chín mươi ngày.
CREATE INDEX ix_notifications_created ON notifications (created_at);
