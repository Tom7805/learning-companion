-- Phiên bản điều khoản, chính sách và đồng ý theo từng mục đích (QTN-03).

CREATE TABLE terms_versions (
    id            UUID PRIMARY KEY,
    document_type VARCHAR(30)  NOT NULL,
    version       VARCHAR(20)  NOT NULL,
    summary       TEXT         NOT NULL,
    effective_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_terms_versions UNIQUE (document_type, version),
    CONSTRAINT ck_terms_versions_type CHECK (document_type IN ('TERMS_OF_SERVICE', 'PRIVACY_POLICY'))
);

-- Trạng thái hiện tại của từng mục đích.
CREATE TABLE consents (
    id               UUID PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    purpose          VARCHAR(40) NOT NULL,
    granted          BOOLEAN     NOT NULL,
    document_version VARCHAR(20) NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_consents_user_purpose UNIQUE (user_id, purpose)
);

-- Lịch sử đồng ý và rút lại, chỉ ghi thêm.
CREATE TABLE consent_history (
    id               UUID PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    purpose          VARCHAR(40) NOT NULL,
    granted          BOOLEAN     NOT NULL,
    document_version VARCHAR(20) NOT NULL,
    recorded_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_consent_history_user ON consent_history (user_id, recorded_at DESC);

INSERT INTO terms_versions (id, document_type, version, summary, effective_at)
VALUES ('6b1d7c2e-0001-4c3a-9f00-000000000001', 'TERMS_OF_SERVICE', '2026-10-01',
        'Bạn dùng Learning Companion để học tập cá nhân, tự chịu trách nhiệm về tài liệu mình tải lên và không dùng hệ thống để phát tán nội dung vi phạm pháp luật.',
        TIMESTAMPTZ '2026-10-01 00:00:00+07'),
       ('6b1d7c2e-0002-4c3a-9f00-000000000002', 'PRIVACY_POLICY', '2026-10-01',
        'Dữ liệu học tập thuộc về bạn, chỉ bạn mở được. Chúng tôi chỉ xử lý dữ liệu cho mục đích bạn đã đồng ý và bạn có thể xuất hoặc xóa dữ liệu bất cứ lúc nào.',
        TIMESTAMPTZ '2026-10-01 00:00:00+07');
