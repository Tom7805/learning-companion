package com.learningcompanion.auth.application;

import com.learningcompanion.auth.domain.AccountStatus;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.auth.domain.UserSession;
import com.learningcompanion.auth.domain.UserSessionRepository;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.security.SessionResolver;
import com.learningcompanion.shared.security.SessionTokenService;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phiên đăng nhập lưu ở máy chủ, trình duyệt chỉ giữ chuỗi bí mật trong cookie HttpOnly.
 * Story này chỉ mở phiên ngay sau khi xác thực thư; đăng nhập và quản lý thiết bị thuộc NCL-01-CN-002.
 */
@Service
public class SessionService implements SessionResolver {

    private final UserSessionRepository sessions;
    private final UserAccountRepository accounts;
    private final SessionTokenService tokens;
    private final AuditLogger audit;
    private final Clock clock;
    private final Duration idleTimeout;

    public SessionService(UserSessionRepository sessions, UserAccountRepository accounts, SessionTokenService tokens,
                          AuditLogger audit, Clock clock, AppProperties properties) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.tokens = tokens;
        this.audit = audit;
        this.clock = clock;
        this.idleTimeout = properties.auth().sessionIdleTimeout();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StartedSession start(UserAccount account, ClientInfo client) {
        String rawToken = tokens.newToken();
        UserSession session = sessions.save(UserSession.startIdleSession(account.getId(), tokens.hash(rawToken),
                Instant.now(clock), idleTimeout, client.userAgent(), client.ipAddress()));
        audit.byUser(account.getId(), AuditAction.SESSION_CREATED, AuditObjectType.SESSION, session.getId(),
                Map.of("reason", "EMAIL_VERIFIED", "rememberDevice", false), client);
        return new StartedSession(session.getId(), rawToken);
    }

    @Override
    @Transactional
    public Optional<AuthenticatedUser> resolve(String rawToken) {
        Instant now = Instant.now(clock);
        return sessions.findByTokenHash(tokens.hash(rawToken))
                .filter(session -> session.isActive(now))
                .flatMap(session -> accounts.findById(session.getUserId())
                        .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                        .map(account -> {
                            session.touch(now, idleTimeout);
                            return new AuthenticatedUser(account.getId(), session.getId(), account.getRole());
                        }));
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> currentAccount(UUID userId) {
        return accounts.findById(userId);
    }

    @Transactional
    public void logout(AuthenticatedUser user, ClientInfo client) {
        sessions.findById(user.sessionId()).ifPresent(session -> {
            session.revoke(Instant.now(clock), "LOGOUT");
            audit.byUser(user.userId(), AuditAction.SESSION_REVOKED, AuditObjectType.SESSION, session.getId(),
                    Map.of("reason", "LOGOUT"), client);
        });
    }

    public record StartedSession(UUID sessionId, String rawToken) {
    }
}
