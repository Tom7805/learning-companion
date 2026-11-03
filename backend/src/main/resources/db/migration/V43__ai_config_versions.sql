-- Cấu hình mô hình và lời nhắc hệ thống theo phiên bản (NCL-04-CN-002, QTN-12). Mỗi lần lưu là một phiên bản nháp
-- mới, không bao giờ sửa phiên bản đã có; mỗi chức năng chỉ có một phiên bản đang phát hành.

create table ai_config_versions (
    id                 uuid primary key,
    feature            varchar(60)   not null,
    version_number     int           not null,
    -- DRAFT nháp, PUBLISHED đang phát hành, SUPERSEDED đã được bản sau thay, REVOKED đã thu hồi khi quay lại bản trước.
    status             varchar(20)   not null,
    -- Nhà cung cấp ưu tiên; trống thì đi theo định tuyến của khả năng.
    provider_code      varchar(40),
    -- Mô hình trên nhà cung cấp ưu tiên; trống thì dùng mô hình mặc định của nhà cung cấp.
    model              varchar(120),
    temperature        numeric(3, 2) not null,
    max_output_tokens  int           not null,
    system_prompt      text          not null,
    -- Phiên bản làm gốc khi soạn, để xem khác biệt.
    based_on_version   int,
    created_at         timestamptz   not null,
    -- Trống với phiên bản đầu tiên do hệ thống đăng ký khi chức năng được xây dựng.
    created_by         uuid,
    last_trial_at      timestamptz,
    published_at       timestamptz,
    published_by       uuid,
    revoked_at         timestamptz,
    revoked_by         uuid,
    version            bigint        not null default 0,
    constraint ai_config_versions_number_unique unique (feature, version_number),
    constraint ai_config_versions_status check (status in ('DRAFT', 'PUBLISHED', 'SUPERSEDED', 'REVOKED')),
    constraint ai_config_versions_temperature check (temperature >= 0 and temperature <= 2),
    constraint ai_config_versions_max_tokens check (max_output_tokens between 1 and 32000)
);

-- Chốt ở tầng dữ liệu: một chức năng không bao giờ có hai phiên bản cùng đang phát hành.
create unique index ai_config_versions_one_published on ai_config_versions (feature) where status = 'PUBLISHED';

-- Sổ đo ghi lời gọi chạy theo phiên bản cấu hình nào (không có nội dung lời nhắc).
alter table ai_call_records add column config_version int;
