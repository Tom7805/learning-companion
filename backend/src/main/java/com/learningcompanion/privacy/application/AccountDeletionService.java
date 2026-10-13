package com.learningcompanion.privacy.application;

import com.learningcompanion.auth.application.LoginAttemptService;
import com.learningcompanion.auth.application.SessionService;
import com.learningcompanion.auth.domain.DeviceInfo;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.auth.domain.UserSession;
import com.learningcompanion.auth.domain.UserSessionRepository;
import com.learningcompanion.privacy.domain.AccountDeletionRequest;
import com.learningcompanion.privacy.domain.AccountDeletionRequestRepository;
import com.learningcompanion.privacy.domain.DeletedAccountTombstone;
import com.learningcompanion.privacy.domain.DeletedAccountTombstoneRepository;
import com.learningcompanion.profile.domain.UserProfile;
import com.learningcompanion.profile.domain.UserProfileRepository;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.erasure.PersonalDataEraser;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.OwnershipGuard;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.security.PasswordHasher;
import com.learningcompanion.shared.security.RateLimiter;
import com.learningcompanion.shared.storage.FileStorageService;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Xóa tài khoản có thời gian chờ (NCL-01-CN-009, QTN-35). Người học xác nhận lại mật khẩu rồi tài khoản chuyển sang
 * chờ xóa ba mươi ngày: mọi phiên bị thu hồi, mọi thông báo khác dừng lại. Đăng nhập lại trong thời gian đó thì hủy
 * được; hết hạn thì tác vụ hằng ngày xóa vĩnh viễn tài khoản cùng mọi dữ liệu và chỉ để lại nhật ký đã ẩn danh.
 */
@Service
public class AccountDeletionService {

    public static final Duration GRACE_PERIOD = Duration.ofDays(30);
    /** Tài khoản chưa có mật khẩu (chỉ đăng nhập qua nhà cung cấp) xác nhận bằng một lần đăng nhập mới. */
    public static final Duration REAUTH_WINDOW = Duration.ofMinutes(5);
    private static final Duration WARNING_INTERVAL = Duration.ofHours(1);
    private static final String CONTEXT = "ACCOUNT_DELETION";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final AccountDeletionRequestRepository requests;
    private final DeletedAccountTombstoneRepository tombstones;
    private final UserAccountRepository accounts;
    private final UserSessionRepository sessions;
    private final UserProfileRepository profiles;
    private final SessionService sessionService;
    private final LoginAttemptService loginAttempts;
    private final PasswordHasher passwordHasher;
    private final OwnershipGuard guard;
    private final List<PersonalDataEraser> erasers;
    private final AuditAnonymizer anonymizer;
    private final FileStorageService storage;
    private final AuditLogger audit;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AccountDeletionService(AccountDeletionRequestRepository requests,
                                  DeletedAccountTombstoneRepository tombstones, UserAccountRepository accounts,
                                  UserSessionRepository sessions, UserProfileRepository profiles,
                                  SessionService sessionService, LoginAttemptService loginAttempts,
                                  PasswordHasher passwordHasher, OwnershipGuard guard, List<PersonalDataEraser> erasers,
                                  AuditAnonymizer anonymizer, FileStorageService storage, AuditLogger audit,
                                  RateLimiter rateLimiter, ApplicationEventPublisher events,
                                  PlatformTransactionManager transactions, Clock clock) {
        this.requests = requests;
        this.tombstones = tombstones;
        this.accounts = accounts;
        this.sessions = sessions;
        this.profiles = profiles;
        this.sessionService = sessionService;
        this.loginAttempts = loginAttempts;
        this.passwordHasher = passwordHasher;
        this.guard = guard;
        this.erasers = erasers;
        this.anonymizer = anonymizer;
        this.storage = storage;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.events = events;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Status status(UUID accountId, ClientInfo client) {
        requireSelf(accountId, client);
        return statusOf(accounts.findById(accountId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)));
    }

    /**
     * Bắt đầu thời gian chờ xóa sau khi xác nhận lại danh tính. Thiếu hoặc sai mật khẩu (ví dụ kẻ gian cầm phiên
     * bị đánh cắp) thì từ chối, ghi nhật ký và báo cho chủ tài khoản qua thư (TC-04).
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public Status request(AuthenticatedUser user, UUID accountId, String password, ClientInfo client) {
        requireSelf(accountId, client);
        UserAccount account = accounts.lockById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (account.isPendingDeletion()) {
            return statusOf(account);
        }
        Instant now = clock.instant();
        String verifiedBy = reauthenticate(user, account, password, now, client);

        Instant deleteAt = now.plus(GRACE_PERIOD);
        requests.save(AccountDeletionRequest.schedule(account.getId(), now, deleteAt));
        account.scheduleDeletion(deleteAt, now);
        int revoked = sessionService.revokeAll(account.getId(), null, SessionService.REASON_ACCOUNT_DELETION, client);
        audit.byUser(account.getId(), AuditAction.ACCOUNT_DELETION_REQUESTED, AuditObjectType.USER_ACCOUNT,
                account.getId(), Map.of("scheduledFor", deleteAt.toString(), "revokedSessions", revoked,
                        "verifiedBy", verifiedBy), client);
        events.publishEvent(new AccountDeletionMailer.DeletionScheduled(account.getEmail(), account.getDisplayName(),
                deleteAt, zoneOf(account.getId())));
        return statusOf(account);
    }

    /** Hủy yêu cầu xóa trong thời gian chờ: tài khoản hoạt động lại với nguyên dữ liệu (TC-02). */
    @Transactional
    public Status cancel(UUID accountId, ClientInfo client) {
        requireSelf(accountId, client);
        UserAccount account = accounts.lockById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!account.isPendingDeletion()) {
            return statusOf(account);
        }
        Instant now = clock.instant();
        Instant scheduledFor = account.getDeletionScheduledFor();
        requests.findByUserIdAndStatus(accountId, AccountDeletionRequest.Status.PENDING)
                .ifPresent(request -> request.cancel(now));
        account.cancelDeletion(now);
        audit.byUser(account.getId(), AuditAction.ACCOUNT_DELETION_CANCELLED, AuditObjectType.USER_ACCOUNT,
                account.getId(), Map.of("scheduledFor", scheduledFor.toString()), client);
        events.publishEvent(new AccountDeletionMailer.DeletionCancelled(account.getEmail(), account.getDisplayName()));
        return statusOf(account);
    }

    /**
     * Xóa vĩnh viễn mọi tài khoản đã hết thời gian chờ (TC-03). Mỗi tài khoản trong một giao dịch riêng, nên một
     * tài khoản lỗi không chặn các tài khoản khác. Trả về số tài khoản đã xóa.
     */
    public int purgeDue() {
        Instant now = clock.instant();
        List<UUID> due = tx.execute(status -> requests
                .findByStatusAndScheduledForLessThanEqualOrderByScheduledForAsc(AccountDeletionRequest.Status.PENDING,
                        now)
                .stream().map(AccountDeletionRequest::getId).toList());
        int purged = 0;
        for (UUID requestId : due == null ? List.<UUID>of() : due) {
            try {
                if (Boolean.TRUE.equals(tx.execute(status -> purge(requestId, now)))) {
                    purged++;
                }
            } catch (RuntimeException ex) {
                log.error("Purging deletion request {} failed", requestId, ex);
            }
        }
        return purged;
    }

    private boolean purge(UUID requestId, Instant now) {
        AccountDeletionRequest request = requests.findById(requestId).filter(found -> found.isDue(now)).orElse(null);
        if (request == null) {
            return false;
        }
        UserAccount account = accounts.lockById(request.getUserId()).filter(UserAccount::isPendingDeletion)
                .orElse(null);
        if (account == null) {
            return false;
        }
        PersonalDataEraser.Subject subject = new PersonalDataEraser.Subject(account.getId(), account.getEmail());
        List<String> files = new ArrayList<>();
        Map<String, Integer> erased = new LinkedHashMap<>();
        for (PersonalDataEraser eraser : erasers) {
            erased.put(eraser.section(), eraser.erase(subject, files));
        }
        UUID anonymousId = UUID.randomUUID();
        int anonymized = anonymizer.anonymize(account.getId(), anonymousId);
        tombstones.save(DeletedAccountTombstone.of(anonymousId, request.getRequestedAt(), now, erased));
        String email = account.getEmail();
        String displayName = account.getDisplayName();
        accounts.delete(account);
        audit.bySystem(AuditAction.ACCOUNT_DELETION_COMPLETED, AuditObjectType.USER_ACCOUNT, anonymousId,
                Map.of("requestedAt", request.getRequestedAt().toString(), "erased", erased,
                        "auditEntriesAnonymized", anonymized));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                files.forEach(AccountDeletionService.this::deleteQuietly);
            }
        });
        events.publishEvent(new AccountDeletionMailer.AccountDeleted(email, displayName));
        return true;
    }

    private String reauthenticate(AuthenticatedUser user, UserAccount account, String password, Instant now,
                                  ClientInfo client) {
        if (account.hasPassword()) {
            if (password == null || password.isEmpty()) {
                throw reject(user, account, "PASSWORD_MISSING", client);
            }
            loginAttempts.ensureNotLocked(account.getEmail(), account.getId(), CONTEXT, client);
            if (!passwordHasher.matches(password, account.getPasswordHash())) {
                reject(user, account, "WRONG_PASSWORD", client);
                throw loginAttempts.recordFailure(account.getEmail(), Optional.of(account), CONTEXT,
                        ErrorCode.CURRENT_PASSWORD_INVALID, "password", client);
            }
            loginAttempts.reset(account.getEmail());
            return "PASSWORD";
        }
        boolean recentSignIn = sessions.findById(user.sessionId())
                .map(session -> !session.getCreatedAt().isBefore(now.minus(REAUTH_WINDOW)))
                .orElse(false);
        if (!recentSignIn) {
            throw reject(user, account, "REAUTHENTICATION_MISSING", client);
        }
        return "RECENT_SIGN_IN";
    }

    /** Ghi nhật ký lần bị từ chối và báo chủ tài khoản (tối đa mỗi giờ một thư để không thành công cụ quấy rối). */
    private BusinessException reject(AuthenticatedUser user, UserAccount account, String reason, ClientInfo client) {
        audit.byUser(account.getId(), AuditAction.ACCOUNT_DELETION_REJECTED, AuditObjectType.USER_ACCOUNT,
                account.getId(), Map.of("reason", reason), client);
        if (rateLimiter.tryAcquire("account-deletion-warning:" + account.getId(), 1, WARNING_INTERVAL)) {
            DeviceInfo device = sessions.findById(user.sessionId()).map(UserSession::device).orElse(null);
            events.publishEvent(new AccountDeletionMailer.DeletionAttemptRejected(account.getEmail(),
                    account.getDisplayName(), clock.instant(), device, zoneOf(account.getId())));
        }
        return new BusinessException(ErrorCode.REAUTHENTICATION_REQUIRED, null,
                Map.of("hasPassword", account.hasPassword()));
    }

    private void requireSelf(UUID accountId, ClientInfo client) {
        guard.requireSelf(accountId, accounts::existsById, AuditObjectType.USER_ACCOUNT, client);
    }

    private Status statusOf(UserAccount account) {
        if (!account.isPendingDeletion()) {
            return new Status(false, null, null);
        }
        Instant requestedAt = requests.findByUserIdAndStatus(account.getId(), AccountDeletionRequest.Status.PENDING)
                .map(AccountDeletionRequest::getRequestedAt).orElse(null);
        return new Status(true, requestedAt, account.getDeletionScheduledFor());
    }

    private ZoneId zoneOf(UUID accountId) {
        try {
            return profiles.findByOwnerId(accountId).map(UserProfile::getTimeZone).map(ZoneId::of).orElse(DEFAULT_ZONE);
        } catch (DateTimeException ex) {
            return DEFAULT_ZONE;
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Could not delete a stored file of a deleted account", ex);
        }
    }

    /** {@code scheduledFor}: thời điểm tài khoản bị xóa vĩnh viễn, chỉ có khi đang chờ xóa. */
    public record Status(boolean pending, Instant requestedAt, Instant scheduledFor) {
    }
}
