package com.learningcompanion.privacy.application;

import com.learningcompanion.privacy.domain.Consent;
import com.learningcompanion.privacy.domain.ConsentHistory;
import com.learningcompanion.privacy.domain.ConsentHistoryRepository;
import com.learningcompanion.privacy.domain.ConsentPurpose;
import com.learningcompanion.privacy.domain.ConsentRepository;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentService {

    private final ConsentRepository consents;
    private final ConsentHistoryRepository history;
    private final AuditLogger audit;
    private final Clock clock;

    public ConsentService(ConsentRepository consents, ConsentHistoryRepository history, AuditLogger audit,
                          Clock clock) {
        this.consents = consents;
        this.history = history;
        this.audit = audit;
        this.clock = clock;
    }

    /** Ghi đồng ý điều khoản và chính sách lúc đăng ký, kèm đúng phiên bản người học đã đọc. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRegistrationConsents(UUID userId, String termsVersion, String privacyVersion,
                                           ClientInfo client) {
        Instant now = Instant.now(clock);
        grant(userId, ConsentPurpose.TERMS_OF_SERVICE, termsVersion, now, client);
        grant(userId, ConsentPurpose.PRIVACY_POLICY, privacyVersion, now, client);
    }

    private void grant(UUID userId, ConsentPurpose purpose, String version, Instant now, ClientInfo client) {
        Consent consent = consents.save(Consent.grant(userId, purpose, version, now));
        history.save(ConsentHistory.of(consent, now));
        audit.byUser(userId, AuditAction.CONSENT_GRANTED, AuditObjectType.CONSENT, consent.getId(),
                Map.of("purpose", purpose.name(), "version", version), client);
    }
}
