package com.learningcompanion.privacy.application;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ẩn danh nhật ký thao tác của một tài khoản bị xóa vĩnh viễn (NCL-01-CN-009, QTN-06): mã tài khoản ở vai người
 * thực hiện hoặc đối tượng được thay bằng một mã ngẫu nhiên không lần ngược được, địa chỉ IP và trình duyệt của
 * người đó bị xóa. Hành động, thời điểm và chi tiết giữ nguyên.
 *
 * <p>Nhật ký vốn chỉ ghi thêm: trigger ở cơ sở dữ liệu chỉ cho đổi các cột trên trong giao dịch có bật
 * {@code app.audit_anonymize}; cờ được tắt ngay sau khi xong và cũng tự mất khi giao dịch kết thúc.
 */
@Component
public class AuditAnonymizer {

    private final JdbcTemplate jdbc;

    public AuditAnonymizer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Trả về số dòng nhật ký đã được ẩn danh. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int anonymize(UUID accountId, UUID anonymousId) {
        jdbc.queryForObject("select set_config('app.audit_anonymize', 'on', true)", String.class);
        int asActor = jdbc.update("""
                update audit_log set actor_id = ?, ip_address = null, user_agent = null
                where actor_id = ?""", anonymousId, accountId);
        // Lượt đăng nhập sai không rõ người gửi vẫn có thể là chính người học: xóa luôn IP và trình duyệt.
        int asObject = jdbc.update("""
                update audit_log set object_id = ?,
                    ip_address = case when actor_type = 'ANONYMOUS' then null else ip_address end,
                    user_agent = case when actor_type = 'ANONYMOUS' then null else user_agent end
                where object_id = ?""", anonymousId, accountId);
        jdbc.queryForObject("select set_config('app.audit_anonymize', 'off', true)", String.class);
        return asActor + asObject;
    }
}
