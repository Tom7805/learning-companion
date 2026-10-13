-- Xóa tài khoản có thời gian chờ (NCL-01-CN-009).

-- Hạn xóa vĩnh viễn nằm ngay trên tài khoản để phiên đăng nhập và màn hình chờ xóa đọc được mà không cần tra thêm.
ALTER TABLE user_accounts ADD COLUMN deletion_scheduled_for TIMESTAMPTZ;
ALTER TABLE user_accounts ADD CONSTRAINT ck_user_accounts_deletion
    CHECK ((status = 'PENDING_DELETION') = (deletion_scheduled_for IS NOT NULL));

-- Mỗi lần yêu cầu xóa: chờ ba mươi ngày rồi xóa, hoặc người học hủy. Bản ghi mất cùng tài khoản khi xóa xong;
-- dấu vết còn lại nằm ở deleted_account_tombstones.
CREATE TABLE account_deletion_requests (
    id            UUID PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    status        VARCHAR(10) NOT NULL,
    requested_at  TIMESTAMPTZ NOT NULL,
    scheduled_for TIMESTAMPTZ NOT NULL,
    cancelled_at  TIMESTAMPTZ,
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_account_deletion_status CHECK (status IN ('PENDING', 'CANCELLED'))
);

CREATE UNIQUE INDEX uq_account_deletion_pending ON account_deletion_requests (user_id) WHERE status = 'PENDING';
CREATE INDEX ix_account_deletion_due ON account_deletion_requests (scheduled_for) WHERE status = 'PENDING';

-- Dấu vết một tài khoản đã bị xóa vĩnh viễn: không có địa chỉ thư, tên hay mã tài khoản cũ.
-- Mã của dấu vết chính là mã ẩn danh thay cho tài khoản trong nhật ký thao tác.
CREATE TABLE deleted_account_tombstones (
    id           UUID PRIMARY KEY,
    requested_at TIMESTAMPTZ NOT NULL,
    deleted_at   TIMESTAMPTZ NOT NULL,
    -- Số mục đã xóa theo từng phần dữ liệu, không có nội dung.
    erased       JSONB       NOT NULL DEFAULT '{}'::jsonb
);

-- Đường riêng có kiểm soát để ẩn danh nhật ký khi xóa tài khoản (đã hẹn ở V3): chỉ trong giao dịch có bật
-- app.audit_anonymize, chỉ được đổi mã người thực hiện, mã đối tượng và xóa địa chỉ IP, trình duyệt.
-- Hành động, thời điểm và chi tiết không bao giờ đổi được; xóa dòng vẫn bị chặn.
CREATE OR REPLACE FUNCTION audit_log_append_only() RETURNS trigger AS
$$
BEGIN
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
