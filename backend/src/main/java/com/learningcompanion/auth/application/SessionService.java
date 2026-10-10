package com.learningcompanion.auth.application;

import com.learningcompanion.auth.domain.AccountStatus;
import com.learningcompanion.auth.domain.DeviceInfo;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.auth.domain.UserSession;
import com.learningcompanion.auth.domain.UserSessionRepository;
import com.learningcompanion.auth.infrastructure.GeoIpLocator;
import com.learningcompanion.auth.infrastructure.UserAgentParser;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.security.SessionResolver;
import com.learningcompanion.shared.security.SessionTokenService;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phiên đăng nhập lưu ở máy chủ, trình duyệt chỉ giữ chuỗi bí mật trong cookie HttpOnly.
 * Mỗi phiên gắn với một thiết bị (cookie thiết bị riêng) để người học xem và đăng xuất từ xa.
 */
@Service
public class SessionService implements SessionResolver {

    public static final String REASON_LOGOUT = "LOGOUT";
    public static final String REASON_REVOKED_BY_USER = "REVOKED_BY_USER";
    public static final String REASON_REVOKED_OTHERS = "REVOKED_OTHERS";
    public static final String REASON_REVOKED_FROM_EMAIL = "REVOKED_FROM_EMAIL";

    private final UserSessionRepository sessions;
    private final UserAccountRepository accounts;
    private final SessionTokenService tokens;
    private final UserAgentParser userAgentParser;
    private final GeoIpLocator geoIpLocator;
    private final AuditLogger audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AppProperties.Auth settings;

    public SessionService(UserSessionRepository sessions, UserAccountRepository accounts, SessionTokenService tokens,
                          UserAgentParser userAgentParser, GeoIpLocator geoIpLocator, AuditLogger audit,
                          ApplicationEventPublisher events, Clock clock, AppProperties properties) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.tokens = tokens;
        this.userAgentParser = userAgentParser;
        this.geoIpLocator = geoIpLocator;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.settings = properties.auth();
    }

    /**
     * Mở phiên trên một thiết bị. Thiết bị chưa từng dùng với tài khoản (mà tài khoản đã có phiên trước đó)
     * được coi là thiết bị mới: chủ tài khoản nhận thư cảnh báo kèm nút đăng xuất thiết bị đó.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StartedSession start(UserAccount account, ClientInfo client, boolean rememberDevice, String rawDeviceId,
                                String reason) {
        Instant now = Instant.now(clock);
        String deviceIdHash = rawDeviceId == null ? null : tokens.hash(rawDeviceId);
        boolean hadSessions = sessions.existsByUserId(account.getId());
        boolean knownDevice = deviceIdHash != null
                && sessions.existsByUserIdAndDeviceIdHash(account.getId(), deviceIdHash);
        DeviceInfo device = describe(client);
        Duration lifetime = rememberDevice ? settings.rememberDeviceDuration() : settings.sessionIdleTimeout();

        String rawToken = tokens.newToken();
        UserSession session = UserSession.start(account.getId(), tokens.hash(rawToken), now, rememberDevice, lifetime,
                deviceIdHash, device, client.userAgent(), client.ipAddress());
        boolean alert = hadSessions && !knownDevice;
        String revokeLink = null;
        if (alert) {
            revokeLink = tokens.newToken();
            session.attachRevokeLink(tokens.hash(revokeLink));
        }
        sessions.save(session);
        audit.byUser(account.getId(), AuditAction.SESSION_CREATED, AuditObjectType.SESSION, session.getId(),
                Map.of("reason", reason, "rememberDevice", rememberDevice, "newDevice", alert), client);
        if (alert) {
            events.publishEvent(new AuthMailNotifier.NewDeviceLoginAlert(account.getEmail(),
                    account.getDisplayName(), device, now, revokeLink));
            audit.byUser(account.getId(), AuditAction.NEW_DEVICE_ALERT_SENT, AuditObjectType.SESSION,
                    session.getId(), Map.of("device", device.label()), client);
        }
        return new StartedSession(session.getId(), rawToken, rememberDevice, lifetime);
    }

    @Override
    @Transactional
    public SessionLookup resolve(String rawToken) {
        Instant now = Instant.now(clock);
        Optional<UserSession> found = sessions.findByTokenHash(tokens.hash(rawToken));
        if (found.isEmpty()) {
            return SessionLookup.invalid(Problem.UNKNOWN);
        }
        UserSession session = found.get();
        switch (session.state(now)) {
            case EXPIRED:
                return SessionLookup.invalid(Problem.EXPIRED);
            case REVOKED:
                return SessionLookup.invalid(REASON_LOGOUT.equals(session.getRevokeReason())
                        ? Problem.UNKNOWN : Problem.REVOKED);
            default:
                break;
        }
        return accounts.findById(session.getUserId())
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .map(account -> {
                    session.touch(now, settings.sessionIdleTimeout());
                    return SessionLookup.valid(new AuthenticatedUser(account.getId(), session.getId(),
                            account.getRole()));
                })
                .orElse(SessionLookup.invalid(Problem.REVOKED));
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> currentAccount(UUID userId) {
        return accounts.findById(userId);
    }

    @Transactional(readOnly = true)
    public List<UserSession> activeSessions(UUID userId) {
        return sessions.findActiveByUserId(userId, Instant.now(clock));
    }

    @Transactional
    public void logout(AuthenticatedUser user, ClientInfo client) {
        sessions.findById(user.sessionId())
                .ifPresent(session -> revoke(session, user.userId(), REASON_LOGOUT, client));
    }

    /** Đăng xuất một thiết bị của chính mình; phiên của người khác coi như không tồn tại. */
    @Transactional
    public void revoke(AuthenticatedUser user, UUID sessionId, ClientInfo client) {
        UserSession session = sessions.findById(sessionId)
                .filter(found -> found.getUserId().equals(user.userId()))
                .filter(found -> found.isActive(Instant.now(clock)))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        revoke(session, user.userId(),
                session.getId().equals(user.sessionId()) ? REASON_LOGOUT : REASON_REVOKED_BY_USER, client);
    }

    /** Đăng xuất mọi thiết bị khác, giữ phiên đang dùng. */
    @Transactional
    public int revokeOthers(AuthenticatedUser user, ClientInfo client) {
        List<UserSession> others = sessions.findActiveByUserId(user.userId(), Instant.now(clock)).stream()
                .filter(session -> !session.getId().equals(user.sessionId()))
                .toList();
        others.forEach(session -> revoke(session, user.userId(), REASON_REVOKED_OTHERS, client));
        return others.size();
    }

    /** Nút "đăng xuất thiết bị đó" trong thư cảnh báo; bấm lại lần nữa vẫn báo thành công. */
    @Transactional
    public RevokedByLink revokeByLink(String rawLinkToken, ClientInfo client) {
        UserSession session = sessions.findByRevokeLinkHash(tokens.hash(rawLinkToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.REVOKE_LINK_INVALID));
        boolean alreadyRevoked = session.getRevokedAt() != null;
        if (!alreadyRevoked) {
            revoke(session, session.getUserId(), REASON_REVOKED_FROM_EMAIL, client);
        }
        return new RevokedByLink(session.device(), session.getCreatedAt(), alreadyRevoked);
    }

    private void revoke(UserSession session, UUID actorId, String reason, ClientInfo client) {
        session.revoke(Instant.now(clock), reason);
        audit.byUser(actorId, AuditAction.SESSION_REVOKED, AuditObjectType.SESSION, session.getId(),
                Map.of("reason", reason), client);
    }

    private DeviceInfo describe(ClientInfo client) {
        UserAgentParser.Parsed parsed = userAgentParser.parse(client.userAgent());
        GeoIpLocator.Location location = geoIpLocator.locate(client);
        return new DeviceInfo(parsed.browser(), parsed.operatingSystem(), parsed.deviceType(),
                location.description(), location.localNetwork());
    }

    public record StartedSession(UUID sessionId, String rawToken, boolean rememberDevice, Duration lifetime) {
    }

    public record RevokedByLink(DeviceInfo device, Instant signedInAt, boolean alreadyRevoked) {
    }
}
