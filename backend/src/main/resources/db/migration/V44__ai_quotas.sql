-- Hạn mức công bằng cho trí tuệ nhân tạo và dung lượng lưu trữ (NCL-04-CN-003, QTN-13, QTN-14).

-- Hạn mức chung cho mọi người học; bảng chỉ có một dòng.
create table quota_policies (
    id                  smallint     primary key,
    daily_ai_units      int          not null,
    storage_limit_bytes bigint       not null,
    updated_at          timestamptz  not null,
    -- Trống khi còn là giá trị mặc định lúc cài đặt.
    updated_by          uuid,
    version             bigint       not null default 0,
    constraint quota_policies_singleton check (id = 1),
    constraint quota_policies_daily check (daily_ai_units between 1 and 100000),
    constraint quota_policies_storage check (storage_limit_bytes > 0)
);

insert into quota_policies (id, daily_ai_units, storage_limit_bytes, updated_at)
values (1, 50, 1073741824, now());

-- Bảng quy đổi: mỗi loại tác vụ tốn bao nhiêu lượt. Loại mới được thêm khi máy chủ khởi động.
create table quota_task_costs (
    task        varchar(60)  primary key,
    units       int          not null,
    updated_at  timestamptz  not null,
    updated_by  uuid,
    version     bigint       not null default 0,
    constraint quota_task_costs_units check (units between 1 and 100)
);

-- Sổ lượt theo ngày học của từng người học (ngày tính theo múi giờ của người học, QTN-09). Lượt đang giữ chỗ và lượt
-- đã trừ tách riêng; tổng hai cột không bao giờ vượt hạn mức nhờ câu lệnh cập nhật có điều kiện.
create table quota_daily_usage (
    owner_id        uuid  not null references user_accounts (id) on delete cascade,
    study_day       date  not null,
    used_units      int   not null default 0,
    reserved_units  int   not null default 0,
    primary key (owner_id, study_day),
    constraint quota_daily_usage_non_negative check (used_units >= 0 and reserved_units >= 0)
);

-- Mỗi tác vụ tốn lượt một dòng: giữ chỗ khi bắt đầu, trừ khi hoàn tất, hoàn lại khi lỗi hoặc bị hủy.
create table quota_reservations (
    id           uuid         primary key,
    owner_id     uuid         not null references user_accounts (id) on delete cascade,
    task         varchar(60)  not null,
    units        int          not null,
    study_day    date         not null,
    -- RESERVED đang giữ chỗ, COMMITTED đã trừ, RELEASED đã hoàn lại.
    status       varchar(20)  not null,
    -- Lý do hoàn lại: FAILED lỗi dịch vụ, CANCELLED người học hủy, EXPIRED treo quá lâu.
    release_reason varchar(20),
    created_at   timestamptz  not null,
    settled_at   timestamptz,
    constraint quota_reservations_status check (status in ('RESERVED', 'COMMITTED', 'RELEASED')),
    constraint quota_reservations_reason check (release_reason is null
        or release_reason in ('FAILED', 'CANCELLED', 'EXPIRED'))
);

create index quota_reservations_owner_day on quota_reservations (owner_id, study_day);
create index quota_reservations_open on quota_reservations (created_at) where status = 'RESERVED';
