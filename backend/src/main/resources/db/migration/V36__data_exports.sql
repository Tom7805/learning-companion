-- Xuất toàn bộ dữ liệu học tập bằng định dạng mở (NCL-01-CN-008).

-- Một lần yêu cầu xuất. Gói được dựng ở luồng nền, mã hóa trong kho tệp và mở được trong bảy ngày;
-- quá hạn thì tệp bị xóa, bản ghi giữ lại để giao diện báo hết hạn và tính giới hạn mỗi ngày một lần.
CREATE TABLE data_exports (
    id             UUID PRIMARY KEY,
    owner_id       UUID        NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    status         VARCHAR(10) NOT NULL,
    requested_at   TIMESTAMPTZ NOT NULL,
    started_at     TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    expires_at     TIMESTAMPTZ,
    -- Khóa trong kho tệp, chỉ có khi gói sẵn sàng; xóa về trống khi gói hết hạn.
    storage_key    VARCHAR(100),
    size_bytes     BIGINT,
    file_count     INTEGER,
    -- Tên tệp tải về, theo ngày tạo gói ở múi giờ của người học.
    file_name      VARCHAR(100),
    -- Các phần có trong gói và số tệp của từng phần, để giao diện và nhật ký nêu đúng nội dung.
    sections       JSONB       NOT NULL DEFAULT '[]'::jsonb,
    failure_reason VARCHAR(60),
    download_count INTEGER     NOT NULL DEFAULT 0,
    version        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_data_exports_status CHECK (status IN ('QUEUED', 'RUNNING', 'READY', 'FAILED', 'EXPIRED')),
    CONSTRAINT uq_data_exports_storage_key UNIQUE (storage_key)
);

CREATE INDEX ix_data_exports_owner_requested ON data_exports (owner_id, requested_at DESC);
-- Dọn gói quá hạn chỉ cần quét các gói còn tệp.
CREATE INDEX ix_data_exports_ready_expiry ON data_exports (expires_at) WHERE status = 'READY';
