-- Thêm tài liệu từ trang web và văn bản dán (NCL-05-CN-004). Tài liệu lấy từ trang web giữ đường dẫn gốc và thời
-- điểm lấy nội dung để trích dẫn ghi rõ nguồn; tài liệu tải tệp hay dán văn bản để trống hai cột này.
ALTER TABLE documents
    ADD COLUMN source_url VARCHAR(2048),
    ADD COLUMN fetched_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_documents_web_source CHECK ((source_url IS NULL) = (fetched_at IS NULL));
