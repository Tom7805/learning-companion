package com.learningcompanion.admin.api.dto;

import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLog;
import com.learningcompanion.shared.audit.AuditObjectType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Một trang kết quả tra cứu nhật ký; chỉ siêu dữ liệu, không có nội dung học tập (QTN-06, QTN-07). */
public record AuditLogResponse(List<Entry> items, UUID nextCursor) {

    /**
     * Một dòng nhật ký.
     *
     * @param actor   tài khoản thực hiện (nếu có và còn tồn tại)
     * @param subject tài khoản mà thao tác nói tới, ví dụ tài khoản bị nhập sai mật khẩu
     * @param device  trình duyệt, hệ điều hành nhận ra từ User-Agent
     */
    public record Entry(UUID id, Instant occurredAt, AuditLog.ActorType actorType, UUID actorId, Account actor,
                        AuditAction action, AuditAction.Category category, AuditAction.Outcome outcome,
                        AuditObjectType objectType, UUID objectId, Account subject, Device device, String ipAddress,
                        Map<String, Object> details, String requestId) {
    }

    public record Account(UUID id, String email, String displayName) {
    }

    public record Device(String browser, String operatingSystem, String deviceType) {
    }
}
