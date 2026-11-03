package com.learningcompanion.ai.config.domain;

/** Vòng đời một phiên bản cấu hình (NCL-04-CN-002). */
public enum ConfigVersionStatus {
    /** Mới lưu, chưa chạy cho người học. */
    DRAFT,
    /** Đang phát hành: mọi lời gọi mới của chức năng chạy theo phiên bản này. Mỗi chức năng chỉ có một. */
    PUBLISHED,
    /** Từng phát hành rồi được phiên bản khác thay; phát hành lại được. */
    SUPERSEDED,
    /** Bị thu hồi khi quản trị viên quay lại phiên bản trước vì có sự cố; không phát hành lại được. */
    REVOKED
}
