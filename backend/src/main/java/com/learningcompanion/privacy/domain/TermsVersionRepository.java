package com.learningcompanion.privacy.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsVersionRepository extends JpaRepository<TermsVersion, UUID> {

    Optional<TermsVersion> findFirstByDocumentTypeAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
            TermsVersion.DocumentType documentType, Instant now);
}
