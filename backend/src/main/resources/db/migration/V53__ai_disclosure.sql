-- Thông báo đang trò chuyện với hệ thống trí tuệ nhân tạo (NCL-04-CN-005, QTN-23): người học đã đọc phiên bản nào
-- của thông báo. Chưa có dòng thì lần mở trợ lý tiếp theo hiện thông báo trước câu hỏi đầu tiên; nội dung thông báo
-- đổi thì tăng phiên bản để mọi người học đọc lại.
CREATE TABLE ai_disclosure_acknowledgements (
    id              UUID PRIMARY KEY,
    owner_id        UUID        NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    version         INT         NOT NULL,
    acknowledged_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_ai_disclosure_owner UNIQUE (owner_id)
);
