-- Lịch sử hội thoại ở thanh bên (NCL-07-CN-003): ghim hội thoại lên đầu, tìm theo tên và nội dung.
ALTER TABLE assistant_conversations ADD COLUMN pinned_at TIMESTAMPTZ;

CREATE INDEX ix_assistant_conversations_owner_pinned ON assistant_conversations (owner_id, pinned_at DESC NULLS LAST, updated_at DESC)
    WHERE trashed_at IS NULL;
