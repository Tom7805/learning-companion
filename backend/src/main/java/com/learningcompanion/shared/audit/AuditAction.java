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
    AUDIT_LOG_TAMPER_ATTEMPTED(ACCESS, DENIED),
    /** Quản trị viên nhập đúng mật khẩu, chờ mã xác thực bước hai; chi tiết ghi bước đang chờ (nhập mã, thiết lập). */
    ADMIN_TWO_FACTOR_CHALLENGED(LOGIN, SUCCESS),
    /** Thiết lập (hoặc thiết lập lại) ứng dụng sinh mã và nhận bộ mười mã khôi phục mới. */
    ADMIN_TWO_FACTOR_ENROLLED(LOGIN, SUCCESS),
    ADMIN_TWO_FACTOR_FAILED(LOGIN, FAILURE),
    /** Nhập sai mã bước hai đủ số lần: khóa đăng nhập quản trị một thời gian và gửi thư cảnh báo. */
    ADMIN_TWO_FACTOR_LOCKED(LOGIN, FAILURE),
    ADMIN_RECOVERY_CODE_USED(LOGIN, SUCCESS),
    /** Câu lệnh cài đặt trên máy chủ tạo tài khoản quản trị viên. */
    ADMIN_ACCOUNT_CREATED(ADMIN, SUCCESS),
    /** Yêu cầu tạo thêm quản trị viên khi đã có một người; luôn bị từ chối. */
    ADMIN_CREATION_REJECTED(ADMIN, DENIED),
    /** Thêm nhà cung cấp trí tuệ nhân tạo; chi tiết ghi mã, kiểu và khả năng, không bao giờ ghi khóa bí mật. */
    AI_PROVIDER_CREATED(ADMIN, SUCCESS),
    /** Sửa nhà cung cấp; chi tiết ghi giá trị trước và sau của từng trường đã đổi. */
    AI_PROVIDER_UPDATED(ADMIN, SUCCESS),
    AI_PROVIDER_ENABLED(ADMIN, SUCCESS),
    AI_PROVIDER_DISABLED(ADMIN, SUCCESS),
    /** Bật nhà cung cấp chưa đủ điều kiện (chưa xác nhận không huấn luyện, thiếu khóa); luôn bị từ chối. */
    AI_PROVIDER_ENABLE_REJECTED(ADMIN, DENIED),
    /** Đổi định tuyến của một khả năng (chính, dự phòng, thời gian chờ, thử lại, ngắt mạch). */
    AI_ROUTE_CHANGED(ADMIN, SUCCESS),
    /** Quản trị viên chuyển dự phòng bằng tay: đổi vai nhà cung cấp chính và dự phòng. */
    AI_FAILOVER_SWITCHED(ADMIN, SUCCESS),
    /** Hệ thống ngắt mạch một nhà cung cấp sau nhiều lỗi liên tiếp và chuyển lời gọi sang dự phòng. */
    AI_CIRCUIT_OPENED(ADMIN, FAILURE),
    AI_ALERT_ACKNOWLEDGED(ADMIN, SUCCESS),
    /** Hệ thống tạo phiên bản 1 cho chức năng trí tuệ nhân tạo vừa được xây dựng và phát hành luôn. */
    AI_CONFIG_REGISTERED(ADMIN, SUCCESS),
    /**
     * Lưu một phiên bản cấu hình nháp mới; chi tiết ghi số phiên bản, phiên bản gốc, các tham số đổi và số dòng lời
     * nhắc thêm bớt (nội dung đầy đủ nằm trong lịch sử phiên bản).
     */
    AI_CONFIG_DRAFT_SAVED(ADMIN, SUCCESS),
    /** Lưu bị chặn vì lời nhắc chứa dữ liệu cá nhân; chi tiết ghi loại và số đoạn, không ghi giá trị. */
    AI_CONFIG_SAVE_REJECTED(ADMIN, FAILURE),
    /** Phát hành một phiên bản; chi tiết ghi phiên bản mới và phiên bản bị thay. */
    AI_CONFIG_PUBLISHED(ADMIN, SUCCESS),
    /** Quay lại phiên bản phát hành trước; phiên bản đang chạy bị thu hồi. */
    AI_CONFIG_ROLLED_BACK(ADMIN, SUCCESS);

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
