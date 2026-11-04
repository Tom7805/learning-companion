-- Kho tài liệu (NCL-05-CN-001): thư mục nhiều cấp, nhãn màu, gắn sao, trạng thái xử lý và lần mở gần nhất.
-- Xóa thư mục hay tài liệu chỉ đưa vào thùng rác (QTN-18): bản ghi được đánh dấu trashed_at và một mã đợt xóa chung
-- cho cả thư mục lẫn mọi thứ bên trong, để chức năng thùng rác (NCL-02-CN-004) khôi phục đúng cả đợt.
-- Không giới hạn số thư mục, số nhãn và số tài liệu (QTN-14).

CREATE TABLE library_folders (
    id             UUID PRIMARY KEY,
    owner_id       UUID         NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    parent_id      UUID REFERENCES library_folders (id) ON DELETE CASCADE,
    name           VARCHAR(120) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    trashed_at     TIMESTAMPTZ,
    trash_batch_id UUID,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_library_folders_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_library_folders_not_self CHECK (parent_id IS NULL OR parent_id <> id)
);

-- Tên thư mục không trùng trong cùng thư mục cha (không phân biệt hoa thường); thư mục trong thùng rác không tính.
CREATE UNIQUE INDEX uq_library_folders_name
    ON library_folders (owner_id, COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(name))
    WHERE trashed_at IS NULL;
CREATE INDEX ix_library_folders_owner_parent ON library_folders (owner_id, parent_id);

ALTER TABLE documents
    ADD COLUMN folder_id         UUID REFERENCES library_folders (id) ON DELETE SET NULL,
    ADD COLUMN starred           BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN processing_status VARCHAR(20) NOT NULL DEFAULT 'READY',
    ADD COLUMN last_opened_at    TIMESTAMPTZ,
    ADD COLUMN trashed_at        TIMESTAMPTZ,
    ADD COLUMN trash_batch_id    UUID,
    ADD CONSTRAINT ck_documents_processing_status
        CHECK (processing_status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED'));

CREATE INDEX ix_documents_owner_folder ON documents (owner_id, folder_id) WHERE trashed_at IS NULL;
CREATE INDEX ix_documents_owner_starred ON documents (owner_id) WHERE starred AND trashed_at IS NULL;
CREATE INDEX ix_documents_owner_opened ON documents (owner_id, last_opened_at DESC) WHERE trashed_at IS NULL;
CREATE INDEX ix_documents_trash_batch ON documents (owner_id, trash_batch_id) WHERE trash_batch_id IS NOT NULL;

CREATE TABLE library_tags (
    id         UUID PRIMARY KEY,
    owner_id   UUID        NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    name       VARCHAR(40) NOT NULL,
    color      VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_library_tags_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_library_tags_color CHECK (color IN ('SUN', 'LILAC', 'SKY', 'MINT', 'CORAL', 'INK'))
);

CREATE UNIQUE INDEX uq_library_tags_name ON library_tags (owner_id, lower(name));

CREATE TABLE library_document_tags (
    document_id UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    tag_id      UUID NOT NULL REFERENCES library_tags (id) ON DELETE CASCADE,
    PRIMARY KEY (document_id, tag_id)
);

CREATE INDEX ix_library_document_tags_tag ON library_document_tags (tag_id);
