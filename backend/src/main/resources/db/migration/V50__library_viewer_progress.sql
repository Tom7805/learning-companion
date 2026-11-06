-- Tiến độ các bước trích, chia đoạn và lập chỉ mục cho khung xem tài liệu (NCL-05-CN-005).
ALTER TABLE documents ADD COLUMN processing_percent INT NOT NULL DEFAULT 0;
ALTER TABLE documents ADD CONSTRAINT ck_documents_processing_percent
    CHECK (processing_percent BETWEEN 0 AND 100);
UPDATE documents SET processing_percent = 100 WHERE processing_status = 'READY';
