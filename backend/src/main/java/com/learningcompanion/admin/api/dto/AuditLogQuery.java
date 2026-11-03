package com.learningcompanion.admin.api.dto;

import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditObjectType;
import java.time.Instant;
import java.util.UUID;

/**
 * Điều kiện tra cứu nhật ký (NCL-03-CN-001). Mọi điều kiện đều không bắt buộc.
 *
 * @param account  địa chỉ thư hoặc mã tài khoản; khớp dòng do tài khoản đó làm hoặc dòng nói về tài khoản đó
 * @param category nhóm thao tác, ví dụ LOGIN
 * @param action   một loại thao tác cụ thể
 * @param from     từ thời điểm (gồm)
 * @param to       tới thời điểm (không gồm)
 * @param before   mã dòng cuối của trang trước
 */
public record AuditLogQuery(String account, AuditAction.Category category, AuditAction action,
                            AuditObjectType objectType, Instant from, Instant to, UUID before, Integer limit) {
}
