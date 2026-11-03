package com.learningcompanion.ai.gateway;

import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.security.Role;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cảnh báo quản trị viên khi mạch của một nhà cung cấp ngắt (TC-02): lưu cảnh báo để hiện ở trang quản trị, ghi nhật
 * ký thao tác và gửi thư. Cùng một mạch chưa được xác nhận thì không cảnh báo lặp.
 */
@Service
public class AiGatewayAlertService {

    private final AiGatewayAlertRepository alerts;
    private final AiProviderConfigRepository providers;
    private final UserAccountRepository accounts;
    private final AuditLogger audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AiGatewayAlertService(AiGatewayAlertRepository alerts, AiProviderConfigRepository providers,
                                 UserAccountRepository accounts, AuditLogger audit, ApplicationEventPublisher events,
                                 Clock clock) {
        this.alerts = alerts;
        this.providers = providers;
        this.accounts = accounts;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void circuitOpened(AiCapability capability, String providerCode, String fallbackCode) {
        if (alerts.existsByCapabilityAndProviderCodeAndAcknowledgedAtIsNull(capability, providerCode)) {
            return;
        }
        AiGatewayAlert alert = alerts.save(new AiGatewayAlert(capability, providerCode, fallbackCode,
                clock.instant()));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("capability", capability.name());
        details.put("provider", providerCode);
        details.put("fallbackProvider", fallbackCode == null ? "none" : fallbackCode);
        audit.bySystem(AuditAction.AI_CIRCUIT_OPENED, AuditObjectType.AI_PROVIDER,
                providers.findByCode(providerCode).map(AiProviderConfig::getId).orElse(null), details);
        accounts.findFirstByRole(Role.ADMIN).ifPresent(admin -> events.publishEvent(new CircuitOpenedNotice(
                admin.getEmail(), admin.getDisplayName(), capability, providerCode, fallbackCode,
                alert.getOpenedAt())));
    }

    /** Thư cảnh báo gửi sau khi cảnh báo đã được lưu. */
    public record CircuitOpenedNotice(String email, String displayName, AiCapability capability, String providerCode,
                                      String fallbackCode, java.time.Instant openedAt) {
    }
}
