-- Nhật ký thao tác chỉ ghi thêm, không chứa nội dung học tập (QTN-06).

CREATE TABLE audit_log (
    id          UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ  NOT NULL,
    actor_type  VARCHAR(20)  NOT NULL,
    actor_id    UUID,
    action      VARCHAR(60)  NOT NULL,
    object_type VARCHAR(40)  NOT NULL,
    object_id   UUID,
    details     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    ip_address  VARCHAR(64),
    user_agent  VARCHAR(512),
    request_id  VARCHAR(64),
    CONSTRAINT ck_audit_log_actor_type CHECK (actor_type IN ('USER', 'ANONYMOUS', 'SYSTEM'))
);

CREATE INDEX ix_audit_log_actor ON audit_log (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_log_object ON audit_log (object_type, object_id, occurred_at DESC);

-- Chặn sửa và xóa ở tầng cơ sở dữ liệu. Việc ẩn danh khi xóa tài khoản (NCL-01-CN-009)
-- sẽ được mở bằng một đường riêng có kiểm soát trong migration của story đó.
CREATE FUNCTION audit_log_append_only() RETURNS trigger AS
$$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_append_only();
