package com.learningcompanion.auth.application;

import com.learningcompanion.auth.domain.AccountStatus;
import com.learningcompanion.auth.domain.ExternalIdentity;
import com.learningcompanion.auth.domain.ExternalIdentityRepository;
import com.learningcompanion.auth.domain.IdentityProvider;
import com.learningcompanion.auth.domain.OAuthFlow;
import com.learningcompanion.auth.domain.OAuthFlowRepository;
import com.learningcompanion.auth.domain.OAuthPending;
import com.learningcompanion.auth.domain.OAuthPendingRepository;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.auth.infrastructure.OidcProviderClient;
import com.learningcompanion.auth.infrastructure.Pkce;
import com.learningcompanion.privacy.application.ConsentService;
import com.learningcompanion.privacy.application.TermsVersionService;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.security.PasswordHasher;
import com.learningcompanion.shared.security.RateLimiter;
import com.learningcompanion.shared.security.SessionTokenService;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đăng nhập và đăng ký qua nhà cung cấp định danh (NCL-01-CN-007), luồng mã ủy quyền OpenID Connect có PKCE.
 *
 * <ul>
 *   <li>Mã chống giả mạo (state) dùng một lần, hết hạn sau mười phút và gắn với đúng trình duyệt đã bắt đầu
 *       qua cookie riêng; không khớp thì từ chối và ghi nhật ký (TC-03).</li>
 *   <li>Lần đầu dùng: hỏi đồng ý điều khoản như đăng ký thường rồi mới tạo tài khoản (TC-01).</li>
 *   <li>Địa chỉ thư đã có tài khoản: không bao giờ tự gộp, người học phải nhập mật khẩu một lần (TC-02).</li>
 * </ul>
 */
@Service
public class OAuthLoginService {

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration PENDING_TTL = Duration.ofMinutes(15);
    private static final String LINK_CONTEXT = "OAUTH_LINK";

    private final List<OidcProviderClient> clients;
    private final OAuthFlowRepository flows;
    private final OAuthPendingRepository pendings;
    private final ExternalIdentityRepository identities;
    private final UserAccountRepository accounts;
    private final SessionService sessions;
    private final LoginAttemptService loginAttempts;
    private final PasswordHasher passwordHasher;
    private final ConsentService consents;
    private final TermsVersionService terms;
    private final SessionTokenService tokens;
    private final RateLimiter rateLimiter;
    private final AuditLogger audit;
    private final AppProperties properties;
    private final Clock clock;

    public OAuthLoginService(List<OidcProviderClient> clients, OAuthFlowRepository flows,
                             OAuthPendingRepository pendings, ExternalIdentityRepository identities,
                             UserAccountRepository accounts, SessionService sessions,
                             LoginAttemptService loginAttempts, PasswordHasher passwordHasher,
                             ConsentService consents, TermsVersionService terms, SessionTokenService tokens,
                             RateLimiter rateLimiter, AuditLogger audit, AppProperties properties, Clock clock) {
        this.clients = clients;
        this.flows = flows;
        this.pendings = pendings;
        this.identities = identities;
        this.accounts = accounts;
        this.sessions = sessions;
        this.loginAttempts = loginAttempts;
        this.passwordHasher = passwordHasher;
        this.consents = consents;
        this.terms = terms;
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    /** Nhà cung cấp đang bật, để giao diện biết có hiện nút hay không. */
    public List<IdentityProvider> enabledProviders() {
        return properties.oauth().enabled() ? clients.stream().map(OidcProviderClient::provider).toList() : List.of();
    }

    /**
     * Bắt đầu một lượt ủy quyền. Trả về địa chỉ trang cho phép quyền và mã gắn trình duyệt (đặt vào cookie).
     * Liên kết từ phần cài đặt cần đang đăng nhập.
     */
    @Transactional
    public Started start(String providerId, OAuthFlow.Mode mode, boolean rememberDevice, String returnTo,
                         UUID currentUserId, ClientInfo client) {
        OidcProviderClient provider = client(providerId);
        rateLimiter.check("oauth:" + client.ipAddress(), properties.auth().loginLimitPerHour(), HOUR);
        if (mode == OAuthFlow.Mode.LINK && currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        String state = tokens.newToken();
        String browser = tokens.newToken();
        String verifier = tokens.newToken() + tokens.newToken();
        flows.save(OAuthFlow.start(tokens.hash(state), tokens.hash(browser), provider.provider(), mode,
                mode == OAuthFlow.Mode.LINK ? currentUserId : null, verifier, rememberDevice, safeReturnTo(returnTo),
                clock.instant(), properties.oauth().flowTtl()));
        String url = provider.authorizationUrl(state, Pkce.challengeOf(verifier),
                redirectUri(provider.provider()));
        return new Started(url, browser);
    }

    /**
     * Nhà cung cấp chuyển người dùng về kèm mã ủy quyền và mã chống giả mạo. Mọi bất thường của mã chống
     * giả mạo đều bị từ chối như nhau và ghi nhật ký kèm lý do, không lộ chi tiết cho người gọi.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public Result callback(String providerId, String code, String state, String error, String browserToken,
                           UUID currentUserId, String deviceId, ClientInfo client) {
        OidcProviderClient provider = client(providerId);
        Instant now = clock.instant();
        OAuthFlow flow = verifiedFlow(provider.provider(), state, browserToken, now, client);
        if (error != null && !error.isBlank()) {
            throw new BusinessException(ErrorCode.OAUTH_CANCELLED);
        }
        OidcProviderClient.ExternalProfile profile = provider.exchange(code, flow.getCodeVerifier(),
                redirectUri(provider.provider()));
        if (!profile.emailVerified()) {
            throw new BusinessException(ErrorCode.OAUTH_EMAIL_UNVERIFIED);
        }
        String email = EmailNormalizer.normalize(profile.email());

        if (flow.getMode() == OAuthFlow.Mode.LINK) {
            return linkFromSettings(flow, currentUserId, profile, email, now, client);
        }

        Optional<ExternalIdentity> known = identities.findByProviderAndSubject(provider.provider(), profile.subject());
        if (known.isPresent()) {
            ExternalIdentity identity = known.get();
            // Tài khoản đang chờ xóa vẫn đăng nhập được để quyết định có hủy yêu cầu xóa không (NCL-01-CN-009).
            UserAccount account = accounts.findById(identity.getUserId())
                    .filter(UserAccount::canSignIn)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_UNAVAILABLE));
            identity.markUsed(email, now);
            return signIn(account, flow.isRememberDevice(), deviceId, flow.getReturnTo(), provider.provider(), client);
        }

        Optional<UserAccount> sameEmail = accounts.findByEmail(email);
        OAuthPending.Kind kind = sameEmail.isPresent() ? OAuthPending.Kind.LINK : OAuthPending.Kind.SIGNUP;
        String token = tokens.newToken();
        pendings.save(OAuthPending.create(tokens.hash(token), kind, provider.provider(), profile.subject(), email,
                profile.name(), flow.isRememberDevice(), flow.getReturnTo(), now, PENDING_TTL));
        return kind == OAuthPending.Kind.LINK
                ? Result.linkRequired(token, email, provider.provider(), flow.getReturnTo())
                : Result.termsRequired(token, email, suggestedName(profile.name(), email), provider.provider(),
                        flow.getReturnTo());
    }

    /** Người mới đồng ý điều khoản: tạo tài khoản với địa chỉ thư đã được nhà cung cấp xác thực (TC-01). */
    @Transactional(noRollbackFor = BusinessException.class)
    public Result completeSignup(String token, String displayName, boolean acceptTerms, String termsVersion,
                                 String privacyVersion, String deviceId, ClientInfo client) {
        Instant now = clock.instant();
        OAuthPending pending = openPending(token, OAuthPending.Kind.SIGNUP, now);
        if (!acceptTerms) {
            throw new BusinessException(ErrorCode.TERMS_NOT_ACCEPTED, "acceptTerms");
        }
        TermsVersionService.CurrentTerms current = terms.current();
        if (!current.terms().getVersion().equals(termsVersion)
                || !current.privacy().getVersion().equals(privacyVersion)) {
            throw new BusinessException(ErrorCode.TERMS_VERSION_OUTDATED, "acceptTerms");
        }
        String name = RegistrationService.normalizeDisplayName(displayName);
        if (name.isEmpty()) {
            throw new BusinessException(ErrorCode.DISPLAY_NAME_REQUIRED, "displayName");
        }
        if (name.codePointCount(0, name.length()) > 50) {
            throw new BusinessException(ErrorCode.DISPLAY_NAME_TOO_LONG, "displayName");
        }
        if (accounts.findByEmail(pending.getEmail()).isPresent()) {
            // Vừa có người đăng ký bằng địa chỉ này: chuyển sang bước nhập mật khẩu, không tự gộp.
            pending.consume(now);
            String linkToken = tokens.newToken();
            pendings.save(OAuthPending.create(tokens.hash(linkToken), OAuthPending.Kind.LINK, pending.getProvider(),
                    pending.getSubject(), pending.getEmail(), pending.getDisplayName(), pending.isRememberDevice(),
                    pending.getReturnTo(), now, PENDING_TTL));
            return Result.linkRequired(linkToken, pending.getEmail(), pending.getProvider(), pending.getReturnTo());
        }
        ensureIdentityFree(pending.getProvider(), pending.getSubject());
        pending.consume(now);
        UserAccount account = accounts.save(UserAccount.registerFromProvider(pending.getEmail(), name, now));
        audit.byUser(account.getId(), AuditAction.ACCOUNT_REGISTERED, AuditObjectType.USER_ACCOUNT, account.getId(),
                Map.of("role", account.getRole().name(), "status", account.getStatus().name(),
                        "method", pending.getProvider().name()), client);
        consents.recordRegistrationConsents(account.getId(), termsVersion, privacyVersion, client);
        link(account, pending.getProvider(), pending.getSubject(), pending.getEmail(), "SIGNUP", now, client);
        return signIn(account, pending.isRememberDevice(), deviceId, pending.getReturnTo(), pending.getProvider(),
                client);
    }

    /**
     * Địa chỉ thư đã có tài khoản dùng mật khẩu: nhập đúng mật khẩu một lần thì liên kết rồi đăng nhập (TC-02).
     * Nhập sai được tính như một lần đăng nhập sai, nên không ai đoán thử mật khẩu qua đường này được.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public Result confirmLink(String token, String password, String deviceId, ClientInfo client) {
        Instant now = clock.instant();
        OAuthPending pending = openPending(token, OAuthPending.Kind.LINK, now);
        String email = pending.getEmail();
        Optional<UserAccount> account = accounts.findByEmail(email);
        loginAttempts.ensureNotLocked(email, account.map(UserAccount::getId).orElse(null), LINK_CONTEXT, client);
        if (account.isEmpty() || !passwordHasher.matches(password, account.get().getPasswordHash())) {
            throw loginAttempts.recordFailure(email, account, LINK_CONTEXT, ErrorCode.INVALID_CREDENTIALS, "password",
                    client);
        }
        loginAttempts.reset(email);
        UserAccount owner = account.get();
        ensureIdentityFree(pending.getProvider(), pending.getSubject());
        if (identities.findByUserIdAndProvider(owner.getId(), pending.getProvider()).isPresent()) {
            throw new BusinessException(ErrorCode.EXTERNAL_IDENTITY_ALREADY_LINKED);
        }
        pending.consume(now);
        // Nhà cung cấp đã xác thực địa chỉ thư và người học vừa chứng minh là chủ tài khoản.
        owner.markEmailVerified(now);
        if (!owner.canSignIn()) {
            throw new BusinessException(ErrorCode.ACCOUNT_UNAVAILABLE);
        }
        link(owner, pending.getProvider(), pending.getSubject(), email, "PASSWORD_CONFIRMED", now, client);
        return signIn(owner, pending.isRememberDevice(), deviceId, pending.getReturnTo(), pending.getProvider(), client);
    }

    private Result linkFromSettings(OAuthFlow flow, UUID currentUserId, OidcProviderClient.ExternalProfile profile,
                                    String email, Instant now, ClientInfo client) {
        // Lượt liên kết chỉ hoàn tất được trong đúng phiên đăng nhập đã bắt đầu nó.
        if (currentUserId == null || !currentUserId.equals(flow.getUserId())) {
            rejected(flow.getUserId(), "SESSION_MISMATCH", client);
        }
        UserAccount account = activeAccount(currentUserId);
        ensureIdentityFree(flow.getProvider(), profile.subject());
        if (identities.findByUserIdAndProvider(account.getId(), flow.getProvider()).isPresent()) {
            throw new BusinessException(ErrorCode.EXTERNAL_IDENTITY_ALREADY_LINKED);
        }
        link(account, flow.getProvider(), profile.subject(), email, "SETTINGS", now, client);
        return Result.linked(flow.getProvider(), flow.getReturnTo());
    }

    private OAuthFlow verifiedFlow(IdentityProvider provider, String state, String browserToken, Instant now,
                                   ClientInfo client) {
        if (state == null || state.isBlank()) {
            rejected(null, "MISSING_STATE", client);
        }
        Optional<OAuthFlow> found = flows.findByStateHash(tokens.hash(state));
        if (found.isEmpty()) {
            rejected(null, "UNKNOWN_STATE", client);
        }
        OAuthFlow flow = found.get();
        String reason = null;
        if (flow.getProvider() != provider) {
            reason = "PROVIDER_MISMATCH";
        } else if (browserToken == null || !tokens.hash(browserToken).equals(flow.getBrowserHash())) {
            reason = "BROWSER_MISMATCH";
        } else if (flow.isConsumed()) {
            reason = "REUSED";
        } else if (flow.isExpired(now)) {
            reason = "EXPIRED";
        }
        if (reason == null && flows.consume(flow.getId(), now) == 0) {
            reason = "REUSED";
        }
        if (reason != null) {
            rejected(flow.getUserId(), reason, client);
        }
        return flow;
    }

    private void rejected(UUID userId, String reason, ClientInfo client) {
        Map<String, Object> details = Map.of("reason", reason);
        if (userId != null) {
            audit.byUser(userId, AuditAction.OAUTH_LOGIN_REJECTED, AuditObjectType.SESSION, null, details, client);
        } else {
            audit.byAnonymous(AuditAction.OAUTH_LOGIN_REJECTED, AuditObjectType.SESSION, null, details, client);
        }
        throw new BusinessException(ErrorCode.OAUTH_STATE_INVALID);
    }

    private OAuthPending openPending(String token, OAuthPending.Kind kind, Instant now) {
        return Optional.ofNullable(token)
                .filter(value -> !value.isBlank())
                .flatMap(value -> pendings.findByTokenHash(tokens.hash(value)))
                .filter(pending -> pending.getKind() == kind && pending.isOpen(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.OAUTH_PENDING_INVALID));
    }

    private void ensureIdentityFree(IdentityProvider provider, String subject) {
        if (identities.findByProviderAndSubject(provider, subject).isPresent()) {
            throw new BusinessException(ErrorCode.EXTERNAL_IDENTITY_IN_USE);
        }
    }

    private void link(UserAccount account, IdentityProvider provider, String subject, String email, String method,
                      Instant now, ClientInfo client) {
        ExternalIdentity identity = identities.save(ExternalIdentity.link(account.getId(), provider, subject, email,
                now));
        audit.byUser(account.getId(), AuditAction.EXTERNAL_IDENTITY_LINKED, AuditObjectType.EXTERNAL_IDENTITY,
                identity.getId(), Map.of("provider", provider.name(), "method", method), client);
    }

    private Result signIn(UserAccount account, boolean rememberDevice, String deviceId, String returnTo,
                          IdentityProvider provider, ClientInfo client) {
        SessionService.StartedSession session = sessions.start(account, client, rememberDevice, deviceId,
                "OAUTH_" + provider.name());
        audit.byUser(account.getId(), AuditAction.LOGIN_SUCCEEDED, AuditObjectType.SESSION, session.sessionId(),
                Map.of("rememberDevice", rememberDevice, "method", provider.name()), client);
        return Result.signedIn(account, session, provider, returnTo);
    }

    private UserAccount activeAccount(UUID userId) {
        UserAccount account = accounts.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_UNAVAILABLE));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_UNAVAILABLE);
        }
        return account;
    }

    private OidcProviderClient client(String providerId) {
        if (!properties.oauth().enabled()) {
            throw new BusinessException(ErrorCode.OAUTH_DISABLED);
        }
        IdentityProvider provider = IdentityProvider.fromId(providerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OAUTH_DISABLED));
        return clients.stream().filter(candidate -> candidate.provider() == provider).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.OAUTH_DISABLED));
    }

    private String redirectUri(IdentityProvider provider) {
        return properties.frontendUrl() + "/auth/callback/" + provider.id();
    }

    /** Chỉ quay về trang trong ứng dụng, không bao giờ sang trang khác. */
    private static String safeReturnTo(String returnTo) {
        if (returnTo == null || !returnTo.startsWith("/") || returnTo.startsWith("//") || returnTo.contains("\\")
                || returnTo.length() > 200) {
            return null;
        }
        return returnTo;
    }

    private static String suggestedName(String name, String email) {
        String candidate = RegistrationService.normalizeDisplayName(name);
        if (candidate.isEmpty()) {
            candidate = email.substring(0, email.indexOf('@'));
        }
        return candidate.codePointCount(0, candidate.length()) > 50
                ? candidate.substring(0, candidate.offsetByCodePoints(0, 50)) : candidate;
    }

    public record Started(String authorizationUrl, String browserToken) {
    }

    public enum Status { SIGNED_IN, LINKED, TERMS_REQUIRED, LINK_REQUIRED }

    public record Result(Status status, UserAccount account, SessionService.StartedSession session,
                         String pendingToken, String email, String displayName, IdentityProvider provider,
                         String returnTo) {

        static Result signedIn(UserAccount account, SessionService.StartedSession session, IdentityProvider provider,
                               String returnTo) {
            return new Result(Status.SIGNED_IN, account, session, null, account.getEmail(), account.getDisplayName(),
                    provider, returnTo);
        }

        static Result linked(IdentityProvider provider, String returnTo) {
            return new Result(Status.LINKED, null, null, null, null, null, provider, returnTo);
        }

        static Result termsRequired(String token, String email, String displayName, IdentityProvider provider,
                                    String returnTo) {
            return new Result(Status.TERMS_REQUIRED, null, null, token, email, displayName, provider, returnTo);
        }

        static Result linkRequired(String token, String email, IdentityProvider provider, String returnTo) {
            return new Result(Status.LINK_REQUIRED, null, null, token, email, null, provider, returnTo);
        }
    }
}
