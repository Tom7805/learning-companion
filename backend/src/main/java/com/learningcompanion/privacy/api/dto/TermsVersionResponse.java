package com.learningcompanion.privacy.api.dto;

import com.learningcompanion.privacy.application.TermsVersionService;
import com.learningcompanion.privacy.domain.TermsVersion;
import java.time.Instant;
import java.util.List;

public record TermsVersionResponse(Document terms, Document privacy) {

    public record Document(String version, String summary, List<String> highlights, List<String> changes,
                           Instant effectiveAt) {

        public static Document of(TermsVersion version) {
            return new Document(version.getVersion(), version.getSummary(), version.getHighlights(),
                    version.getChanges(), version.getEffectiveAt());
        }
    }

    public static TermsVersionResponse of(TermsVersionService.CurrentTerms current) {
        return new TermsVersionResponse(Document.of(current.terms()), Document.of(current.privacy()));
    }
}
