package com.learningcompanion.auth.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByTokenHash(String tokenHash);

    Optional<UserSession> findByRevokeLinkHash(String revokeLinkHash);

    List<UserSession> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    boolean existsByUserIdAndDeviceIdHash(UUID userId, String deviceIdHash);

    /** Phiên còn hiệu lực của người học, phiên dùng gần nhất lên đầu. */
    @Query("""
            select s from UserSession s
            where s.userId = :userId and s.revokedAt is null and s.expiresAt > :now
            order by s.lastActiveAt desc""")
    List<UserSession> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
