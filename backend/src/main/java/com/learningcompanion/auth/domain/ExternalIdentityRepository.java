package com.learningcompanion.auth.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {

    Optional<ExternalIdentity> findByProviderAndSubject(IdentityProvider provider, String subject);

    Optional<ExternalIdentity> findByUserIdAndProvider(UUID userId, IdentityProvider provider);

    List<ExternalIdentity> findByUserIdOrderByLinkedAtAsc(UUID userId);
}
