-- Trích văn bản, nhận dạng chữ, chia đoạn và lập chỉ mục tài liệu (NCL-05-CN-003). Tác vụ nền nhận tài liệu chờ xử
-- lý, trích văn bản theo loại tệp (trang quét và ảnh đi qua nhận dạng chữ), chia đoạn theo đề mục và độ dài, rồi tạo
-- biểu diễn ngữ nghĩa cho từng đoạn. Mỗi tài liệu chỉ được xử lý một lần dù được dùng ở bao nhiêu nơi.

ALTER TABLE documents
    -- Lý do lỗi khi xử lý (mã cố định, giao diện dịch ra lời), kèm trang lỗi nếu lỗi nằm ở một trang.
    ADD COLUMN processing_error      VARCHAR(40),
    ADD COLUMN processing_error_page INT,
    ADD COLUMN processing_started_at TIMESTAMPTZ,
    ADD COLUMN processed_at          TIMESTAMPTZ,
    ADD COLUMN processing_attempts   INT NOT NULL DEFAULT 0,
    -- Số trang (tài liệu dạng trang), số trang trình chiếu, số ảnh hoặc số đoạn văn của tài liệu đã xử lý.
    ADD COLUMN unit_count            INT,
    ADD COLUMN chunk_count           INT NOT NULL DEFAULT 0,
    -- Nhà cung cấp đã tạo vector: chỉ so được vector của cùng một nhà cung cấp.
    ADD COLUMN index_provider        VARCHAR(40),
    ADD CONSTRAINT ck_documents_processing_error_page CHECK (processing_error_page IS NULL OR processing_error_page > 0);

-- Hàng đợi của tác vụ nền: tài liệu chờ xử lý hoặc đang xử lý quá lâu (máy chủ dừng giữa chừng).
CREATE INDEX ix_documents_processing_queue ON documents (created_at)
    WHERE processing_status IN ('PENDING', 'PROCESSING') AND trashed_at IS NULL;

-- Đoạn văn của tài liệu: luôn gắn vị trí trong tài liệu (QTN-20) và tách theo chủ sở hữu (QTN-08). Vector lưu dạng
-- mảng số thực; tìm kiếm theo nghĩa chỉ so trong các đoạn của cùng chủ sở hữu.
CREATE TABLE document_chunks (
    id              UUID PRIMARY KEY,
    document_id     UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    owner_id        UUID         NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    ordinal         INT          NOT NULL,
    -- PAGE: trang của tài liệu dạng trang; SLIDE: trang trình chiếu; IMAGE: ảnh; PARAGRAPH: đoạn văn (Word, sách điện
    -- tử, văn bản thuần), khi đó location_end là đoạn cuối mà đoạn chia này bao gồm.
    location_kind   VARCHAR(16)  NOT NULL,
    location_number INT          NOT NULL,
    location_end    INT          NOT NULL,
    heading         VARCHAR(300),
    content         TEXT         NOT NULL,
    -- TEXT: lấy từ lớp chữ của tệp; OCR: nhận dạng từ hình.
    source          VARCHAR(10)  NOT NULL,
    embedding       REAL[]       NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_document_chunks_ordinal UNIQUE (document_id, ordinal),
    CONSTRAINT ck_document_chunks_kind CHECK (location_kind IN ('PAGE', 'SLIDE', 'IMAGE', 'PARAGRAPH')),
    CONSTRAINT ck_document_chunks_location CHECK (location_number > 0 AND location_end >= location_number),
    CONSTRAINT ck_document_chunks_source CHECK (source IN ('TEXT', 'OCR')),
    CONSTRAINT ck_document_chunks_content CHECK (length(content) > 0)
);

CREATE INDEX ix_document_chunks_owner_document ON document_chunks (owner_id, document_id, ordinal);
