-- Hỏi đáp có căn cứ trên tài liệu (NCL-07-CN-001): hội thoại thuộc một không gian học hoặc là hội thoại riêng, mỗi câu
-- trả lời lưu cùng các đoạn đã trích. Tài liệu trích dẫn được ghi lại tiêu đề và vị trí lúc trả lời, để trích dẫn vẫn
-- đọc được khi tài liệu sau đó bị xóa (NCL-07-CN-002).

CREATE TABLE assistant_conversations (
    id           UUID PRIMARY KEY,
    owner_id     UUID         NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    workspace_id UUID REFERENCES workspaces (id) ON DELETE CASCADE,
    title        VARCHAR(120) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    trashed_at   TIMESTAMPTZ,
    version      BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX ix_assistant_conversations_owner ON assistant_conversations (owner_id, updated_at DESC)
    WHERE trashed_at IS NULL;
CREATE INDEX ix_assistant_conversations_workspace ON assistant_conversations (owner_id, workspace_id)
    WHERE trashed_at IS NULL;

CREATE TABLE assistant_messages (
    id              UUID PRIMARY KEY,
    owner_id        UUID        NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    conversation_id UUID        NOT NULL REFERENCES assistant_conversations (id) ON DELETE CASCADE,
    role            VARCHAR(16) NOT NULL,
    content         TEXT        NOT NULL DEFAULT '',
    status          VARCHAR(16) NOT NULL,
    -- Câu hỏi: các tài liệu đang chọn lúc hỏi (phạm vi truy xuất).
    document_ids    UUID[]      NOT NULL DEFAULT '{}',
    -- Câu trả lời: ba câu hỏi gợi ý, mỗi câu một dòng.
    suggestions     TEXT        NOT NULL DEFAULT '',
    error_code      VARCHAR(40),
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_assistant_messages_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_assistant_messages_status CHECK (status IN
        ('SENT', 'STREAMING', 'COMPLETE', 'STOPPED', 'FAILED', 'BLOCKED', 'SUPPORT', 'NOT_FOUND', 'NO_SOURCES'))
);

CREATE INDEX ix_assistant_messages_conversation ON assistant_messages (owner_id, conversation_id, created_at);

CREATE TABLE assistant_citations (
    id              UUID PRIMARY KEY,
    owner_id        UUID         NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    message_id      UUID         NOT NULL REFERENCES assistant_messages (id) ON DELETE CASCADE,
    number          INT          NOT NULL,
    -- Tài liệu bị xóa hẳn thì còn giữ tiêu đề và vị trí để báo "tài liệu không còn".
    document_id     UUID REFERENCES documents (id) ON DELETE SET NULL,
    chunk_id        UUID REFERENCES document_chunks (id) ON DELETE SET NULL,
    document_title  VARCHAR(200) NOT NULL,
    location_kind   VARCHAR(16)  NOT NULL,
    location_number INT          NOT NULL,
    location_end    INT          NOT NULL,
    passage         INT          NOT NULL,
    excerpt         TEXT         NOT NULL,
    CONSTRAINT uq_assistant_citations UNIQUE (message_id, number)
);

CREATE INDEX ix_assistant_citations_document ON assistant_citations (owner_id, document_id);
