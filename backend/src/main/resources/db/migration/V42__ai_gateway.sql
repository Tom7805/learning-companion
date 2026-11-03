-- Lớp kết nối dịch vụ trí tuệ nhân tạo (NCL-04-CN-001): nhà cung cấp, định tuyến từng khả năng, sổ đo mức sử dụng
-- và cảnh báo ngắt mạch. Không bảng nào lưu nội dung lời gọi hay khóa bí mật (chỉ tên khóa trong kho bí mật).

create table ai_providers (
    id                        uuid primary key,
    code                      varchar(40)    not null unique,
    display_name              varchar(80)    not null,
    adapter                   varchar(30)    not null,
    base_url                  varchar(300),
    text_model                varchar(120),
    embedding_model           varchar(120),
    vision_model              varchar(120),
    -- Danh sách khả năng, cách nhau bằng dấu phẩy, ví dụ TEXT_GENERATION,EMBEDDING.
    capabilities              varchar(200)   not null,
    -- Tên khóa trong kho bí mật (biến môi trường), không bao giờ là giá trị khóa.
    secret_name               varchar(120),
    input_price_per_million   numeric(12, 4) not null default 0,
    output_price_per_million  numeric(12, 4) not null default 0,
    -- Xác nhận cam kết không dùng dữ liệu để huấn luyện (QTN-11): thiếu thì không bật được.
    no_training_confirmed_at  timestamptz,
    no_training_confirmed_by  uuid,
    enabled                   boolean        not null default false,
    created_at                timestamptz    not null,
    updated_at                timestamptz    not null,
    version                   bigint         not null default 0
);

create table ai_capability_routes (
    capability                 varchar(30) primary key,
    primary_provider_id        uuid        not null references ai_providers (id),
    fallback_provider_id       uuid references ai_providers (id),
    timeout_seconds            int         not null,
    max_retries                int         not null,
    breaker_failure_threshold  int         not null,
    breaker_window_seconds     int         not null,
    breaker_open_seconds       int         not null,
    updated_at                 timestamptz not null,
    version                    bigint      not null default 0,
    constraint ai_route_distinct_providers check (fallback_provider_id is null
        or fallback_provider_id <> primary_provider_id)
);

-- Một dòng cho mỗi lời gọi: số đơn vị, độ trễ, chi phí và nhà cung cấp; không có nội dung (TC-01).
create table ai_call_records (
    id              uuid primary key,
    occurred_at     timestamptz not null,
    capability      varchar(30) not null,
    feature         varchar(60) not null,
    provider_code   varchar(40),
    outcome         varchar(20) not null,
    used_fallback   boolean     not null,
    attempts        int         not null,
    input_units     int         not null,
    output_units    int         not null,
    latency_ms      int         not null,
    cost_micro_usd  bigint      not null,
    error_code      varchar(40)
);

create index ai_call_records_occurred_idx on ai_call_records (occurred_at desc);

create table ai_gateway_alerts (
    id                      uuid primary key,
    capability              varchar(30) not null,
    provider_code           varchar(40) not null,
    fallback_provider_code  varchar(40),
    opened_at               timestamptz not null,
    acknowledged_at         timestamptz,
    acknowledged_by         uuid
);

create index ai_gateway_alerts_open_idx on ai_gateway_alerts (opened_at desc);

-- Môi trường thử và máy phát triển dùng hai bản mô phỏng trả kết quả cố định; môi trường thật thêm nhà cung cấp
-- thật trong trang quản trị rồi đổi định tuyến.
insert into ai_providers (id, code, display_name, adapter, capabilities, input_price_per_million,
                          output_price_per_million, no_training_confirmed_at, enabled, created_at, updated_at)
values ('6f1d0c1e-0000-4000-8000-000000000001', 'mock-primary', 'Bản mô phỏng chính', 'MOCK',
        'TEXT_GENERATION,EMBEDDING,VISION,OCR', 0.5000, 1.5000, now(), true, now(), now()),
       ('6f1d0c1e-0000-4000-8000-000000000002', 'mock-backup', 'Bản mô phỏng dự phòng', 'MOCK',
        'TEXT_GENERATION,EMBEDDING,VISION,OCR', 0.2500, 1.0000, now(), true, now(), now());

insert into ai_capability_routes (capability, primary_provider_id, fallback_provider_id, timeout_seconds, max_retries,
                                  breaker_failure_threshold, breaker_window_seconds, breaker_open_seconds, updated_at)
select capability, '6f1d0c1e-0000-4000-8000-000000000001', '6f1d0c1e-0000-4000-8000-000000000002',
       30, 1, 5, 60, 60, now()
from (values ('TEXT_GENERATION'), ('EMBEDDING'), ('VISION'), ('OCR')) as caps (capability);
