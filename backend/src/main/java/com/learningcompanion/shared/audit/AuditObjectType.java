package com.learningcompanion.shared.audit;

public enum AuditObjectType {
    USER_ACCOUNT,
    VERIFICATION_TOKEN,
    CONSENT,
    SESSION,
    DOCUMENT,
    PROFILE,
    EXTERNAL_IDENTITY,
    DATA_EXPORT,
    NOTIFICATION,
    UI_PREFERENCES,
    /** Một điểm cuối của hệ thống, ví dụ khu quản trị; mã đối tượng để trống, đường dẫn nằm trong chi tiết. */
    ENDPOINT,
    AUDIT_LOG
}
