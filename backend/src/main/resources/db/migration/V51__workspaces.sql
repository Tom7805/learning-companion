-- Không gian học (NCL-06-CN-001): mỗi điều cần học một nơi làm việc riêng, gắn tài liệu chọn từ kho.
-- Không giới hạn số không gian học (QTN-14). Tài liệu chỉ lưu và xử lý một lần trong kho; không gian học chỉ giữ liên
-- kết tới tài liệu (QTN-17). Xóa không gian học chỉ đưa vào thùng rác (QTN-18), tài liệu vẫn ở kho.

CREATE TABLE workspaces (
    id                UUID PRIMARY KEY,
    owner_id          UUID         NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    name              VARCHAR(100) NOT NULL,
    icon              VARCHAR(24)  NOT NULL,
    color             VARCHAR(16)  NOT NULL,
    description       VARCHAR(500) NOT NULL DEFAULT '',
    -- Thư mục trong kho nhận tệp tải lên ngay tại không gian học; tạo khi lần đầu cần tới.
    default_folder_id UUID REFERENCES library_folders (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    last_opened_at    TIMESTAMPTZ,
    trashed_at        TIMESTAMPTZ,
    trash_batch_id    UUID,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_workspaces_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_workspaces_color CHECK (color IN ('SUN', 'LILAC', 'SKY', 'MINT', 'CORAL', 'INK'))
);

CREATE INDEX ix_workspaces_owner ON workspaces (owner_id) WHERE trashed_at IS NULL;
CREATE INDEX ix_workspaces_trash_batch ON workspaces (owner_id, trash_batch_id) WHERE trash_batch_id IS NOT NULL;

CREATE TABLE workspace_documents (
    id           UUID PRIMARY KEY,
    owner_id     UUID        NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    workspace_id UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    document_id  UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    attached_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_workspace_documents UNIQUE (workspace_id, document_id)
);

CREATE INDEX ix_workspace_documents_document ON workspace_documents (owner_id, document_id);
