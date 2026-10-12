-- Quản lý đồng ý theo mục đích (NCL-01-CN-006).

-- Điểm chính dễ hiểu của từng phiên bản và các điểm thay đổi so với bản trước,
-- hiển thị trong trang quyền riêng tư và hộp thoại khi điều khoản lên phiên bản mới.
ALTER TABLE terms_versions
    ADD COLUMN highlights JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN changes    JSONB NOT NULL DEFAULT '[]'::jsonb;

UPDATE terms_versions
SET highlights = '["Bạn dùng Learning Companion cho việc học của chính mình.",
                   "Bạn chịu trách nhiệm về tài liệu mình đưa lên và có quyền sử dụng chúng.",
                   "Không dùng hệ thống để phát tán nội dung vi phạm pháp luật hoặc xâm phạm quyền của người khác.",
                   "Bạn có thể ngừng dùng và xóa tài khoản bất cứ lúc nào."]'::jsonb
WHERE document_type = 'TERMS_OF_SERVICE' AND version = '2026-10-01';

UPDATE terms_versions
SET highlights = '["Dữ liệu học tập thuộc về bạn, chỉ bạn mở được, kể cả quản trị viên cũng không xem nội dung.",
                   "Chúng tôi chỉ xử lý dữ liệu cho những mục đích bạn đã đồng ý; các mục đích không bắt buộc đều tắt sẵn.",
                   "Bạn bật hoặc tắt từng mục đích bất cứ lúc nào và thay đổi có hiệu lực ngay.",
                   "Bạn có thể xuất toàn bộ dữ liệu hoặc xóa tài khoản bất cứ lúc nào."]'::jsonb
WHERE document_type = 'PRIVACY_POLICY' AND version = '2026-10-01';

-- Thống kê sử dụng ẩn danh: chỉ ghi khi người học đã đồng ý, và không lưu bất kỳ mã nào
-- trỏ về người học (không mã tài khoản, không địa chỉ IP, không mã bản ghi trong đường dẫn).
CREATE TABLE usage_events (
    id          UUID PRIMARY KEY,
    event       VARCHAR(40)  NOT NULL,
    route       VARCHAR(120) NOT NULL,
    occurred_on DATE         NOT NULL
);

CREATE INDEX ix_usage_events_day ON usage_events (occurred_on, event);
