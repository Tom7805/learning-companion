-- Lọc nội dung không an toàn và chống chèn chỉ dẫn (NCL-04-CN-004, QTN-15, QTN-16).

-- Ngưỡng lọc của từng nhóm nội dung; quản trị viên đổi được, không tắt được. Nhóm tự gây hại không có ngưỡng: câu hỏi
-- có dấu hiệu tự gây hại luôn nhận câu trả lời ân cần kèm nguồn trợ giúp.
create table content_safety_levels (
    category    varchar(30)  primary key,
    -- STRICT chặn cả dấu hiệu nhẹ, STANDARD chặn dấu hiệu rõ, LENIENT chỉ chặn yêu cầu trắng trợn.
    level       varchar(20)  not null,
    updated_at  timestamptz  not null,
    updated_by  uuid,
    version     bigint       not null default 0,
    constraint content_safety_levels_category check (category in
        ('VIOLENCE', 'HATE', 'SEXUAL', 'HARMFUL_INSTRUCTIONS', 'FRAUD')),
    constraint content_safety_levels_level check (level in ('STRICT', 'STANDARD', 'LENIENT'))
);

insert into content_safety_levels (category, level, updated_at) values
    ('VIOLENCE', 'STANDARD', now()),
    ('HATE', 'STANDARD', now()),
    ('SEXUAL', 'STANDARD', now()),
    ('HARMFUL_INSTRUCTIONS', 'STANDARD', now()),
    ('FRAUD', 'STANDARD', now());

-- Mỗi lần bộ lọc can thiệp một dòng: chỉ ghi loại vi phạm và mã đối tượng, không bao giờ ghi nội dung câu hỏi, câu
-- trả lời hay đoạn nguồn.
create table ai_safety_events (
    id           uuid         primary key,
    occurred_at  timestamptz  not null,
    owner_id     uuid         references user_accounts (id) on delete set null,
    feature      varchar(60)  not null,
    -- INPUT_BLOCKED câu hỏi bị chặn, OUTPUT_BLOCKED nội dung sinh ra bị thay, SELF_HARM_SUPPORT trả lời ân cần,
    -- INJECTION_FLAGGED đoạn nguồn nghi chèn chỉ dẫn.
    kind         varchar(30)  not null,
    category     varchar(30),
    -- Kiểu chèn chỉ dẫn khi kind là INJECTION_FLAGGED.
    signal       varchar(40),
    object_type  varchar(40),
    object_id    uuid,
    constraint ai_safety_events_kind check (kind in
        ('INPUT_BLOCKED', 'OUTPUT_BLOCKED', 'SELF_HARM_SUPPORT', 'INJECTION_FLAGGED'))
);

create index ai_safety_events_recent on ai_safety_events (occurred_at desc);
