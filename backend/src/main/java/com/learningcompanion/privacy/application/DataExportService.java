package com.learningcompanion.privacy.application;

import com.learningcompanion.auth.application.LoginAttemptService;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.auth.domain.UserSessionRepository;
import com.learningcompanion.privacy.domain.DataExportRequest;
import com.learningcompanion.privacy.domain.DataExportRequestRepository;
import com.learningcompanion.privacy.domain.DataExportStatus;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.exception.GlobalExceptionHandler;
import com.learningcompanion.shared.ownership.OwnershipGuard;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.security.PasswordHasher;
import com.learningcompanion.shared.security.SignedUrlService;
import com.learningcompanion.shared.storage.FileStorageService;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Xuất toàn bộ dữ liệu học tập (NCL-01-CN-008, QTN-32). Mỗi ngày một yêu cầu; gói được dựng ở luồng nền,
 * mở được trong bảy ngày và mỗi lần tải phải xác nhận lại danh tính. Việc xuất không gọi tới trí tuệ nhân tạo
 * nên không bao giờ tính vào hạn mức dùng AI (TC-02).
 */
@Service
public class DataExportService {

    public static final Duration REQUEST_INTERVAL = Duration.ofDays(1);
    public static final Duration VALID_FOR = Duration.ofDays(7);
    /** Vừa đăng nhập trong khoảng này thì không phải nhập lại mật khẩu để tải. */
    public static final Duration REAUTH_WINDOW = Duration.ofMinutes(5);
    /** Quá khoảng này mà luồng nền chưa xong thì coi như thất bại để người học yêu cầu lại được. */
    public static final Duration STALL_LIMIT = Duration.ofMinutes(30);
    private static final String DOWNLOAD_CONTEXT = "DATA_EXPORT_DOWNLOAD";

    private final DataExportRequestRepository exports;
    private final UserAccountRepository accounts;
    private final UserSessionRepository sessions;
    private final OwnershipGuard guard;
    private final FileStorageService storage;
    private final SignedUrlService signedUrls;
    private final PasswordHasher passwordHasher;
    private final LoginAttemptService loginAttempts;
    private final AuditLogger audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DataExportService(DataExportRequestRepository exports, UserAccountRepository accounts,
                             UserSessionRepository sessions, OwnershipGuard guard, FileStorageService storage,
                             SignedUrlService signedUrls, PasswordHasher passwordHasher,
                             LoginAttemptService loginAttempts, AuditLogger audit, ApplicationEventPublisher events,
                             Clock clock) {
        this.exports = exports;
        this.accounts = accounts;
        this.sessions = sessions;
        this.guard = guard;
        this.storage = storage;
        this.signedUrls = signedUrls;
        this.passwordHasher = passwordHasher;
        this.loginAttempts = loginAttempts;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /** Lần xuất gần nhất và lúc nào được yêu cầu tiếp, cho mục Tài khoản trong cài đặt. */
    @Transactional
    public Overview overview(AuthenticatedUser user) {
        Instant now = clock.instant();
        Optional<DataExportRequest> latest = exports.findFirstByOwnerIdOrderByRequestedAtDesc(user.userId());
        latest.ifPresent(export -> refresh(export, now));
        return new Overview(latest.orElse(null), nextAvailableAt(user.userId(), now).orElse(null));
    }

    /**
     * Nhận yêu cầu xuất và giao cho luồng nền. Dòng tài khoản được khóa tới hết giao dịch nên hai lần bấm
     * cùng lúc không tạo ra hai gói.
     */
    @Transactional
    public Overview request(AuthenticatedUser user, ClientInfo client) {
        UUID ownerId = user.userId();
        accounts.lockById(ownerId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        Instant now = clock.instant();
        exports.findFirstByOwnerIdOrderByRequestedAtDesc(ownerId).ifPresent(export -> refresh(export, now));
        Optional<Instant> next = nextAvailableAt(ownerId, now);
        if (next.isPresent()) {
            long seconds = Math.max(1, Duration.between(now, next.get()).toSeconds());
            throw new BusinessException(ErrorCode.EXPORT_DAILY_LIMIT, null, Map.of(
                    "nextAvailableAt", next.get().toString(),
                    GlobalExceptionHandler.RETRY_AFTER_SECONDS, seconds));
        }
        DataExportRequest export = exports.save(DataExportRequest.queue(ownerId, now));
        audit.byUser(ownerId, AuditAction.DATA_EXPORT_REQUESTED, AuditObjectType.DATA_EXPORT, export.getId(),
                Map.of(), client);
        events.publishEvent(new DataExportJob.ExportQueued(export.getId(), ownerId));
        return new Overview(export, now.plus(REQUEST_INTERVAL));
    }

    /** Một lần xuất của chính người học; của người khác thì trả lời như không tồn tại và ghi nhật ký (TC-04). */
    @Transactional(noRollbackFor = BusinessException.class)
    public DataExportRequest get(UUID id, ClientInfo client) {
        DataExportRequest export = guard.require(exports, id, AuditObjectType.DATA_EXPORT, client);
        refresh(export, clock.instant());
        return export;
    }

    /**
     * Cấp đường dẫn tải gói, sống năm phút. Mỗi lần tải phải xác nhận lại danh tính: nhập đúng mật khẩu, hoặc
     * vừa đăng nhập lại trong năm phút qua (tài khoản chỉ đăng nhập qua nhà cung cấp định danh). Nhập sai mật khẩu
     * được đếm chung với đăng nhập nên không dò được mật khẩu qua đây.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public SignedUrlService.SignedLink download(AuthenticatedUser user, UUID id, String password, ClientInfo client) {
        DataExportRequest export = guard.require(exports, id, AuditObjectType.DATA_EXPORT, client);
        Instant now = clock.instant();
        refresh(export, now);
        if (export.getStatus() == DataExportStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.EXPORT_EXPIRED);
        }
        if (export.getStatus() != DataExportStatus.READY) {
            throw new BusinessException(ErrorCode.EXPORT_NOT_READY);
        }
        String method = reauthenticate(user, password, now, client);
        export.recordDownload();
        audit.byUser(user.userId(), AuditAction.DATA_EXPORT_DOWNLOADED, AuditObjectType.DATA_EXPORT, export.getId(),
                Map.of("verifiedBy", method, "downloadCount", export.getDownloadCount()), client);
        return signedUrls.sign(export.getId(), export.getOwnerId());
    }

    /**
     * Mở gói bằng đường dẫn ký. Gói vẫn được tra kèm chủ sở hữu ghi trong chữ ký, và phải còn hạn bảy ngày:
     * đường dẫn ký còn hạn của một gói vừa quá hạn cũng không mở được.
     */
    @Transactional(readOnly = true)
    public Download open(String token) {
        SignedUrlService.Claims claims = signedUrls.verify(token);
        DataExportRequest export = exports.findByIdAndOwnerId(claims.objectId(), claims.ownerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (export.getStatus() != DataExportStatus.READY || export.isPastExpiry(clock.instant())
                || export.getStorageKey() == null) {
            throw new BusinessException(ErrorCode.EXPORT_EXPIRED);
        }
        return new Download(export.getFileName(), storage.read(export.getStorageKey()));
    }

    /**
     * Cập nhật trạng thái theo thời gian: gói quá bảy ngày thì xóa tệp và chuyển sang hết hạn, luồng nền dừng
     * giữa chừng thì chuyển sang thất bại. Gọi trong giao dịch ghi.
     */
    public void refresh(DataExportRequest export, Instant now) {
        if (export.isPastExpiry(now)) {
            export.expire().ifPresent(this::deleteAfterCommit);
            audit.bySystem(AuditAction.DATA_EXPORT_EXPIRED, AuditObjectType.DATA_EXPORT, export.getId(),
                    Map.of("reason", "VALIDITY_ENDED"));
        } else if (export.isStalled(now, STALL_LIMIT)) {
            export.fail("STALLED", now);
            audit.bySystem(AuditAction.DATA_EXPORT_FAILED, AuditObjectType.DATA_EXPORT, export.getId(),
                    Map.of("reason", "STALLED"));
        }
    }

    /** Xóa tệp khỏi kho sau khi giao dịch ghi xong, để bản ghi và kho không bao giờ lệch nhau khi có lỗi. */
    public void deleteAfterCommit(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storage.delete(key);
                }
            });
        } else {
            storage.delete(key);
        }
    }

    private Optional<Instant> nextAvailableAt(UUID ownerId, Instant now) {
        return exports.findFirstByOwnerIdAndStatusNotOrderByRequestedAtDesc(ownerId, DataExportStatus.FAILED)
                .map(last -> last.getRequestedAt().plus(REQUEST_INTERVAL))
                .filter(next -> next.isAfter(now));
    }

    private String reauthenticate(AuthenticatedUser user, String password, Instant now, ClientInfo client) {
        UserAccount account = accounts.findById(user.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        if (password != null && !password.isEmpty() && account.hasPassword()) {
            loginAttempts.ensureNotLocked(account.getEmail(), account.getId(), DOWNLOAD_CONTEXT, client);
            if (!passwordHasher.matches(password, account.getPasswordHash())) {
                throw loginAttempts.recordFailure(account.getEmail(), Optional.of(account), DOWNLOAD_CONTEXT,
                        ErrorCode.CURRENT_PASSWORD_INVALID, "password", client);
            }
            loginAttempts.reset(account.getEmail());
            return "PASSWORD";
        }
        if (recentlySignedIn(user, now)) {
            return "RECENT_SIGN_IN";
        }
        throw new BusinessException(ErrorCode.REAUTHENTICATION_REQUIRED, null,
                Map.of("hasPassword", account.hasPassword()));
    }

    /** Phiên hiện tại vừa đăng nhập trong năm phút qua thì tải được mà không phải nhập lại mật khẩu. */
    @Transactional(readOnly = true)
    public boolean recentlySignedIn(AuthenticatedUser user) {
        return recentlySignedIn(user, clock.instant());
    }

    private boolean recentlySignedIn(AuthenticatedUser user, Instant now) {
        return sessions.findById(user.sessionId())
                .map(session -> !session.getCreatedAt().isBefore(now.minus(REAUTH_WINDOW)))
                .orElse(false);
    }

    /** {@code nextAvailableAt} trống nghĩa là yêu cầu xuất được ngay. */
    public record Overview(DataExportRequest latest, Instant nextAvailableAt) {
    }

    public record Download(String fileName, byte[] content) {
    }
}
