-- Tra cứu và lưu giữ nhật ký thao tác (NCL-03-CN-001).

-- Quản trị viên lọc theo khoảng thời gian, theo loại thao tác, và theo tài khoản (người thực hiện hoặc đối tượng).
CREATE INDEX ix_audit_log_occurred ON audit_log (occurred_at DESC, id DESC);
CREATE INDEX ix_audit_log_action ON audit_log (action, occurred_at DESC);

-- Nhật ký giữ một năm. Đường riêng có kiểm soát để dọn: chỉ trong giao dịch bật app.audit_retention, và chỉ những
-- dòng đã quá một năm theo đồng hồ của cơ sở dữ liệu. Sửa vẫn chỉ được theo đường ẩn danh đã có ở V37; mọi việc
-- xóa khác vẫn bị chặn.
CREATE OR REPLACE FUNCTION audit_log_append_only() RETURNS trigger AS
$$
BEGIN
    IF TG_OP = 'DELETE'
        AND current_setting('app.audit_retention', true) = 'on'
        AND OLD.occurred_at < now() - INTERVAL '365 days' THEN
        RETURN OLD;
    END IF;
    IF TG_OP = 'UPDATE'
        AND current_setting('app.audit_anonymize', true) = 'on'
        AND NEW.id = OLD.id
        AND NEW.occurred_at = OLD.occurred_at
        AND NEW.actor_type = OLD.actor_type
        AND NEW.action = OLD.action
        AND NEW.object_type = OLD.object_type
        AND NEW.details = OLD.details
        AND NEW.request_id IS NOT DISTINCT FROM OLD.request_id
        AND (NEW.ip_address IS NULL OR NEW.ip_address = OLD.ip_address)
        AND (NEW.user_agent IS NULL OR NEW.user_agent = OLD.user_agent) THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;
