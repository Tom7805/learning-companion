package com.learningcompanion.shared.audit;

import static com.learningcompanion.shared.audit.AuditAction.Category.ACCESS;
import static com.learningcompanion.shared.audit.AuditAction.Category.ACCOUNT;
import static com.learningcompanion.shared.audit.AuditAction.Category.ADMIN;
import static com.learningcompanion.shared.audit.AuditAction.Category.LOGIN;
import static com.learningcompanion.shared.audit.AuditAction.Category.PRIVACY;
import static com.learningcompanion.shared.audit.AuditAction.Category.SESSION;
import static com.learningcompanion.shared.audit.AuditAction.Category.SETTINGS;
import static com.learningcompanion.shared.audit.AuditAction.Outcome.DENIED;
import static com.learningcompanion.shared.audit.AuditAction.Outcome.FAILURE;
import static com.learningcompanion.shared.audit.AuditAction.Outcome.SUCCESS;

/**
 * Loại thao tác trong nhật ký (NCL-03-CN-001). Mỗi loại thuộc một nhóm để quản trị viên lọc nhanh, và có kết quả
 * cố định: thành công, thất bại hay bị từ chối.
 */
public enum AuditAction {
    ACCOUNT_REGISTERED(ACCOUNT, SUCCESS),
    REGISTRATION_ATTEMPT_EXISTING_EMAIL(ACCOUNT, FAILURE),
    VERIFICATION_EMAIL_SENT(ACCOUNT, SUCCESS),
    VERIFICATION_EMAIL_RESENT(ACCOUNT, SUCCESS),
    VERIFICATION_LINK_EXPIRED(ACCOUNT, FAILURE),
    EMAIL_VERIFIED(ACCOUNT, SUCCESS),
    CONSENT_GRANTED(PRIVACY, SUCCESS),
    CONSENT_WITHDRAWN(PRIVACY, SUCCESS),
    LOGIN_SUCCEEDED(LOGIN, SUCCESS),
    LOGIN_FAILED(LOGIN, FAILURE),
    LOGIN_LOCKED(LOGIN, FAILURE),
    NEW_DEVICE_ALERT_SENT(SESSION, SUCCESS),
    PASSWORD_RESET_REQUESTED(ACCOUNT, SUCCESS),
    PASSWORD_RESET_LINK_EXPIRED(ACCOUNT, FAILURE),
    PASSWORD_RESET_COMPLETED(ACCOUNT, SUCCESS),
    PASSWORD_CHANGED(ACCOUNT, SUCCESS),
    SESSION_CREATED(SESSION, SUCCESS),
    SESSION_REVOKED(SESSION, SUCCESS),
    LEARNING_ACCESS_DENIED(ACCESS, DENIED),
    ADMIN_ACCOUNT_VIEWED(ADMIN, SUCCESS),
    PROFILE_UPDATED(SETTINGS, SUCCESS),
    AVATAR_UPDATED(SETTINGS, SUCCESS),
    AVATAR_REMOVED(SETTINGS, SUCCESS),
    OAUTH_LOGIN_REJECTED(LOGIN, FAILURE),
    EXTERNAL_IDENTITY_LINKED(ACCOUNT, SUCCESS),
    EXTERNAL_IDENTITY_UNLINKED(ACCOUNT, SUCCESS),
    DATA_EXPORT_REQUESTED(PRIVACY, SUCCESS),
    DATA_EXPORT_COMPLETED(PRIVACY, SUCCESS),
    DATA_EXPORT_FAILED(PRIVACY, FAILURE),
    DATA_EXPORT_DOWNLOADED(PRIVACY, SUCCESS),
    DATA_EXPORT_EXPIRED(PRIVACY, SUCCESS),
    ACCOUNT_DELETION_REQUESTED(PRIVACY, SUCCESS),
    ACCOUNT_DELETION_REJECTED(PRIVACY, FAILURE),
    ACCOUNT_DELETION_CANCELLED(PRIVACY, SUCCESS),
    ACCOUNT_DELETION_COMPLETED(PRIVACY, SUCCESS),
    /** Đổi giao diện (sáng tối, cỡ chữ, thanh bên); chi tiết ghi giá trị trước và sau. */
    UI_PREFERENCES_CHANGED(SETTINGS, SUCCESS),
    /** Người đã đăng nhập gọi điểm cuối không thuộc vai trò của mình, ví dụ người học mở khu quản trị. */
    ROLE_ACCESS_DENIED(ACCESS, DENIED),
    /** Quản trị viên tra cứu nhật ký; chi tiết ghi các điều kiện lọc. */
    AUDIT_LOG_SEARCHED(ADMIN, SUCCESS),
    /** Có yêu cầu sửa hoặc xóa một dòng nhật ký; luôn bị từ chối. */
    AUDIT_LOG_TAMPER_ATTEMPTED(ACCESS, DENIED);

    /** Nhóm thao tác để lọc nhật ký. */
    public enum Category { LOGIN, SESSION, ACCOUNT, PRIVACY, SETTINGS, ACCESS, ADMIN }

    public enum Outcome { SUCCESS, FAILURE, DENIED }

    private final Category category;
    private final Outcome outcome;

    AuditAction(Category category, Outcome outcome) {
        this.category = category;
        this.outcome = outcome;
    }

    public Category category() {
        return category;
    }

    public Outcome outcome() {
        return outcome;
    }
}
