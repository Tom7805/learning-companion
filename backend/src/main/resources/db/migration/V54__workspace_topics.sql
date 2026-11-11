-- Dàn ý chủ đề của không gian học (NCL-06-CN-002): cây tối đa ba cấp, mỗi chủ đề có mục tiêu học. Chủ đề do trợ lý đề
-- xuất mang cờ origin = AI (nhãn do trí tuệ nhân tạo sinh ra, QTN-23); người học sửa tay thì edited = true và lần đề
-- xuất lại giữ nguyên chủ đề đó. Trích dẫn của chủ đề chụp lại tiêu đề, vị trí và đoạn gốc như trích dẫn của trợ lý.
CREATE TABLE workspace_topics (
    id           UUID PRIMARY KEY,
    owner_id     UUID         NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    workspace_id UUID         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    parent_id    UUID REFERENCES workspace_topics (id) ON DELETE CASCADE,
    position     INT          NOT NULL,
    title        VARCHAR(200) NOT NULL,
    goal         VARCHAR(500) NOT NULL DEFAULT '',
    origin       VARCHAR(8)   NOT NULL,
    edited       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_workspace_topics_origin CHECK (origin IN ('MANUAL', 'AI'))
);

CREATE INDEX ix_workspace_topics_workspace ON workspace_topics (owner_id, workspace_id, parent_id, position);

CREATE TABLE workspace_topic_sources (
    id              UUID PRIMARY KEY,
    owner_id        UUID         NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    topic_id        UUID         NOT NULL REFERENCES workspace_topics (id) ON DELETE CASCADE,
    document_id     UUID REFERENCES documents (id) ON DELETE SET NULL,
    document_title  VARCHAR(300) NOT NULL,
    location_kind   VARCHAR(16)  NOT NULL,
    location_number INT          NOT NULL,
    location_end    INT          NOT NULL,
    passage         INT          NOT NULL,
    excerpt         TEXT         NOT NULL
);

CREATE INDEX ix_workspace_topic_sources_topic ON workspace_topic_sources (owner_id, topic_id);
