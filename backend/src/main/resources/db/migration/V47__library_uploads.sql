-- Tải tài liệu lên kho (NCL-05-CN-002): tải theo từng phần để tiếp tục được khi mạng chập chờn, dấu vân tay nội dung
-- để phát hiện trùng trong toàn kho (QTN-17), dung lượng tối đa một tệp do quản trị viên đặt (QTN-19) và loại sách
-- điện tử.

-- Dung lượng tối đa một tệp, mặc định 50 MiB; quản trị viên đổi cùng các hạn mức chung khác.
ALTER TABLE quota_policies
    ADD COLUMN max_upload_file_bytes BIGINT NOT NULL DEFAULT 52428800,
    ADD CONSTRAINT quota_policies_max_upload CHECK (max_upload_file_bytes > 0);

ALTER TABLE documents
    DROP CONSTRAINT ck_documents_type,
    ADD CONSTRAINT ck_documents_type
        CHECK (document_type IN ('PDF', 'DOCX', 'PPTX', 'EPUB', 'TEXT', 'IMAGE', 'AUDIO', 'VIDEO', 'WEB')),
    -- SHA-256 của tệp gốc dạng hex; tài liệu cũ chưa có thì để trống và không tham gia so trùng.
    ADD COLUMN content_sha256 VARCHAR(64);

CREATE INDEX ix_documents_owner_sha256 ON documents (owner_id, content_sha256)
    WHERE trashed_at IS NULL AND content_sha256 IS NOT NULL;

-- Một lần tải tệp lên: các phần đã nhận nằm mã hóa trong kho tệp tạm, ở đây chỉ giữ tiến độ. Phiên bị bỏ dở quá hạn
-- được dọn cùng các phần đã nhận.
CREATE TABLE library_upload_sessions (
    id              UUID PRIMARY KEY,
    owner_id        UUID         NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    folder_id       UUID REFERENCES library_folders (id) ON DELETE SET NULL,
    file_name       VARCHAR(255) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    chunk_size      INT          NOT NULL,
    received_bytes  BIGINT       NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL,
    document_id     UUID REFERENCES documents (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_library_upload_sessions_size CHECK (size_bytes > 0 AND received_bytes BETWEEN 0 AND size_bytes),
    CONSTRAINT ck_library_upload_sessions_chunk CHECK (chunk_size > 0),
    CONSTRAINT ck_library_upload_sessions_status CHECK (status IN ('UPLOADING', 'COMPLETED'))
);

CREATE INDEX ix_library_upload_sessions_owner ON library_upload_sessions (owner_id, status);
CREATE INDEX ix_library_upload_sessions_expiry ON library_upload_sessions (expires_at) WHERE status = 'UPLOADING';
