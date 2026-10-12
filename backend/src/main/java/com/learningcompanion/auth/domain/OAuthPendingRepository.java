package com.learningcompanion.auth.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthPendingRepository extends JpaRepository<OAuthPending, UUID> {

    Optional<OAuthPending> findByTokenHash(String tokenHash);
}
