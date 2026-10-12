package com.learningcompanion.privacy.application;

import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.privacy.domain.Consent;
import com.learningcompanion.privacy.domain.ConsentHistory;
import com.learningcompanion.privacy.domain.ConsentHistoryRepository;
import com.learningcompanion.privacy.domain.ConsentPurpose;
import com.learningcompanion.privacy.domain.ConsentRepository;
import com.learningcompanion.privacy.domain.TermsVersion;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.OwnershipGuard;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đồng ý theo từng mục đích và quyền rút lại (QTN-03, NCL-01-CN-006). Mỗi lần đồng ý hay rút lại đều ghi
 * vào lịch sử chỉ ghi thêm kèm phiên bản văn bản và thời điểm, đồng thời ghi nhật ký thao tác.
 */
@Service
public class ConsentService {

    /** Các mục đích người học tự bật tắt; đều tắt sẵn cho tới khi người học bật. */
    public static final List<ConsentPurpose> OPTIONAL_PURPOSES = Arrays.stream(ConsentPurpose.values())
            .filter(purpose -> !purpose.isRequired())
            .toList();

    private final ConsentRepository consents;
    private final ConsentHistoryRepository history;
    private final TermsVersionService terms;
    private final UserAccountRepository accounts;
    private final OwnershipGuard guard;
    private final AuditLogger audit;
    private final Clock clock;

    public ConsentService(ConsentRepository consents, ConsentHistoryRepository history, TermsVersionService terms,
                          UserAccountRepository accounts, OwnershipGuard guard, AuditLogger audit, Clock clock) {
        this.consents = consents;
        this.history = history;
        this.terms = terms;
        this.accounts = accounts;
        this.guard = guard;
        this.audit = audit;
        this.clock = clock;
    }

    /** Ghi đồng ý điều khoản và chính sách lúc đăng ký, kèm đúng phiên bản người học đã đọc. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRegistrationConsents(UUID userId, String termsVersion, String privacyVersion,
                                           ClientInfo client) {
        Instant now = Instant.now(clock);
        set(userId, ConsentPurpose.TERMS_OF_SERVICE, true, termsVersion, now, client);
        set(userId, ConsentPurpose.PRIVACY_POLICY, true, privacyVersion, now, client);
    }

    @Transactional(readOnly = true)
    public PrivacyStatus status(UUID accountId, ClientInfo client) {
        requireSelf(accountId, client);
        return statusOf(accountId);
    }

    /** Bật hoặc tắt một mục đích không bắt buộc; có hiệu lực ngay từ yêu cầu kế tiếp. */
    @Transactional
    public PrivacyStatus setPurpose(UUID accountId, ConsentPurpose purpose, boolean granted, ClientInfo client) {
        requireSelf(accountId, client);
        if (purpose.isRequired()) {
            throw new BusinessException(ErrorCode.CONSENT_PURPOSE_REQUIRED, "purpose");
        }
        String version = terms.current().privacy().getVersion();
        set(accountId, purpose, granted, version, Instant.now(clock), client);
        return statusOf(accountId);
    }

    @Transactional(readOnly = true)
    public List<ConsentHistory> history(UUID accountId, ClientInfo client) {
        requireSelf(accountId, client);
        return history.findByUserIdOrderByRecordedAtDesc(accountId);
    }

    /**
     * Đồng ý phiên bản điều khoản và chính sách mới. Phiên bản gửi lên phải đúng bản đang áp dụng:
     * nếu vừa có bản mới hơn trong lúc người học đọc thì báo để đọc lại.
     */
    @Transactional
    public PrivacyStatus acceptCurrentTerms(UUID accountId, String termsVersion, String privacyVersion,
                                           ClientInfo client) {
        requireSelf(accountId, client);
        TermsVersionService.CurrentTerms current = terms.current();
        if (!current.terms().getVersion().equals(termsVersion)
                || !current.privacy().getVersion().equals(privacyVersion)) {
            throw new BusinessException(ErrorCode.TERMS_VERSION_OUTDATED);
        }
        Instant now = Instant.now(clock);
        set(accountId, ConsentPurpose.TERMS_OF_SERVICE, true, termsVersion, now, client);
        set(accountId, ConsentPurpose.PRIVACY_POLICY, true, privacyVersion, now, client);
        return statusOf(accountId);
    }

    /** Người học còn phải đồng ý bản điều khoản hoặc chính sách mới trước khi dùng tiếp ứng dụng. */
    @Transactional(readOnly = true)
    public boolean requiresAcceptance(UUID userId) {
        TermsVersionService.CurrentTerms current = terms.current();
        Map<ConsentPurpose, Consent> mine = byPurpose(userId);
        return !accepted(mine, ConsentPurpose.TERMS_OF_SERVICE, current.terms())
                || !accepted(mine, ConsentPurpose.PRIVACY_POLICY, current.privacy());
    }

    private PrivacyStatus statusOf(UUID userId) {
        TermsVersionService.CurrentTerms current = terms.current();
        Map<ConsentPurpose, Consent> mine = byPurpose(userId);
        List<PurposeState> purposes = OPTIONAL_PURPOSES.stream()
                .map(purpose -> new PurposeState(purpose, Optional.ofNullable(mine.get(purpose))))
                .toList();
        return new PrivacyStatus(userId, current,
                versionOf(mine, ConsentPurpose.TERMS_OF_SERVICE), versionOf(mine, ConsentPurpose.PRIVACY_POLICY),
                !accepted(mine, ConsentPurpose.TERMS_OF_SERVICE, current.terms())
                        || !accepted(mine, ConsentPurpose.PRIVACY_POLICY, current.privacy()),
                purposes);
    }

    private void set(UUID userId, ConsentPurpose purpose, boolean granted, String version, Instant now,
                     ClientInfo client) {
        Optional<Consent> existing = consents.findByUserIdAndPurpose(userId, purpose);
        Consent consent;
        if (existing.isPresent()) {
            consent = existing.get();
            if (!consent.change(granted, version, now)) {
                return;
            }
        } else {
            if (!granted) {
                // Chưa từng bật thì "tắt" không đổi gì: mục đích không bắt buộc vốn tắt sẵn.
                return;
            }
            consent = consents.save(Consent.record(userId, purpose, true, version, now));
        }
        history.save(ConsentHistory.of(consent, now));
        audit.byUser(userId, granted ? AuditAction.CONSENT_GRANTED : AuditAction.CONSENT_WITHDRAWN,
                AuditObjectType.CONSENT, consent.getId(), Map.of("purpose", purpose.name(), "version", version),
                client);
    }

    private void requireSelf(UUID accountId, ClientInfo client) {
        guard.requireSelf(accountId, accounts::existsById, AuditObjectType.USER_ACCOUNT, client);
    }

    private Map<ConsentPurpose, Consent> byPurpose(UUID userId) {
        return consents.findByUserId(userId).stream()
                .collect(Collectors.toMap(Consent::getPurpose, Function.identity()));
    }

    private static boolean accepted(Map<ConsentPurpose, Consent> mine, ConsentPurpose purpose, TermsVersion current) {
        Consent consent = mine.get(purpose);
        return consent != null && consent.isGranted() && consent.getDocumentVersion().equals(current.getVersion());
    }

    private static String versionOf(Map<ConsentPurpose, Consent> mine, ConsentPurpose purpose) {
        Consent consent = mine.get(purpose);
        return consent != null && consent.isGranted() ? consent.getDocumentVersion() : null;
    }

    public record PurposeState(ConsentPurpose purpose, Optional<Consent> consent) {

        public boolean granted() {
            return consent.map(Consent::isGranted).orElse(false);
        }
    }

    public record PrivacyStatus(UUID accountId, TermsVersionService.CurrentTerms current,
                                String acceptedTermsVersion, String acceptedPrivacyVersion,
                                boolean acceptanceRequired, List<PurposeState> purposes) {
    }
}
