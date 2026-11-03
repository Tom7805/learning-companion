package com.learningcompanion.admin.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminLoginChallengeRepository extends JpaRepository<AdminLoginChallenge, UUID> {

    Optional<AdminLoginChallenge> findByTokenHash(String tokenHash);
}
