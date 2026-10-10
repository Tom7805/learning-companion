package com.learningcompanion.privacy.api.dto;

import com.learningcompanion.privacy.application.TermsVersionService;
import com.learningcompanion.privacy.domain.TermsVersion;
import java.time.Instant;

public record TermsVersionResponse(Document terms, Document privacy) {

    public record Document(String version, String summary, Instant effectiveAt) {

        static Document of(TermsVersion version) {
            return new Document(version.getVersion(), version.getSummary(), version.getEffectiveAt());
        }
    }

    public static TermsVersionResponse of(TermsVersionService.CurrentTerms current) {
        return new TermsVersionResponse(Document.of(current.terms()), Document.of(current.privacy()));
    }
}
