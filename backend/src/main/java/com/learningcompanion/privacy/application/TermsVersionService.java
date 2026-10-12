package com.learningcompanion.privacy.application;

import com.learningcompanion.privacy.domain.TermsVersion;
import com.learningcompanion.privacy.domain.TermsVersionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TermsVersionService {

    private final TermsVersionRepository repository;
    private final Clock clock;

    public TermsVersionService(TermsVersionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CurrentTerms current() {
        Instant now = Instant.now(clock);
        return new CurrentTerms(find(TermsVersion.DocumentType.TERMS_OF_SERVICE, now),
                find(TermsVersion.DocumentType.PRIVACY_POLICY, now));
    }

    /** Đăng một phiên bản mới; dùng bởi công cụ môi trường thử, sau này bởi trang quản trị. */
    @Transactional
    public TermsVersion publish(TermsVersion.DocumentType type, String version, String summary,
                                List<String> highlights, List<String> changes, Instant effectiveAt) {
        return repository.save(TermsVersion.publish(type, version, summary, highlights, changes, effectiveAt));
    }

    private TermsVersion find(TermsVersion.DocumentType type, Instant now) {
        return repository.findFirstByDocumentTypeAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(type, now)
                .orElseThrow(() -> new IllegalStateException("No effective " + type + " version configured"));
    }

    public record CurrentTerms(TermsVersion terms, TermsVersion privacy) {
    }
}
