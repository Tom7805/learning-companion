-- Bản ghi học tập đầu tiên: tài liệu của người học (NCL-01-CN-004).
-- Mọi bảng dữ liệu học tập đều có owner_id bắt buộc; ứng dụng chỉ truy vấn kèm owner_id.
-- Story tải tài liệu (NCL-03-CN-001) sẽ bổ sung thư mục, phiên tải lên và trạng thái xử lý.

CREATE TABLE documents (
    id                UUID PRIMARY KEY,
    owner_id          UUID         NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    title             VARCHAR(200) NOT NULL,
    document_type     VARCHAR(20)  NOT NULL,
    content_text      TEXT         NOT NULL DEFAULT '',
    original_filename VARCHAR(255) NOT NULL,
    mime_type         VARCHAR(127) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    -- Khóa trong kho tệp, dạng "{owner_id}/{uuid}"; tệp được mã hóa trước khi ghi.
    storage_key       VARCHAR(100) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_documents_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_documents_size CHECK (size_bytes >= 0),
    CONSTRAINT ck_documents_type CHECK (document_type IN ('PDF', 'DOCX', 'PPTX', 'TEXT', 'IMAGE', 'AUDIO', 'VIDEO', 'WEB'))
);

CREATE INDEX ix_documents_owner_created ON documents (owner_id, created_at DESC);
