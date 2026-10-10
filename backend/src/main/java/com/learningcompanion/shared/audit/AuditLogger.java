package com.learningcompanion.shared.audit;

import com.learningcompanion.shared.web.ClientInfo;
import com.learningcompanion.shared.web.RequestIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi nhật ký thao tác trong cùng giao dịch với thao tác: thao tác thất bại thì nhật ký cũng không được ghi.
 * Chi tiết chỉ chứa mã và trạng thái, không bao giờ chứa mật khẩu, đường dẫn bí mật hay nội dung học tập.
 */
@Component
public class AuditLogger {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditLogger(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void byUser(UUID actorId, AuditAction action, AuditObjectType objectType, UUID objectId,
                       Map<String, Object> details, ClientInfo client) {
        save(AuditLog.ActorType.USER, actorId, action, objectType, objectId, details, client);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void byAnonymous(AuditAction action, AuditObjectType objectType, UUID objectId,
                            Map<String, Object> details, ClientInfo client) {
        save(AuditLog.ActorType.ANONYMOUS, null, action, objectType, objectId, details, client);
    }

    private void save(AuditLog.ActorType actorType, UUID actorId, AuditAction action, AuditObjectType objectType,
                      UUID objectId, Map<String, Object> details, ClientInfo client) {
        repository.save(new AuditLog(Instant.now(clock), actorType, actorId, action, objectType, objectId,
                details == null ? Map.of() : details,
                client == null ? null : client.ipAddress(),
                client == null ? null : client.userAgent(),
                MDC.get(RequestIdFilter.MDC_KEY)));
    }
}
