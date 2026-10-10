package com.learningcompanion.auth.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    Optional<VerificationToken> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Đánh dấu đã dùng một cách nguyên tử: hai lần bấm cùng lúc thì chỉ một lần thành công. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update VerificationToken t set t.usedAt = :now
            where t.id = :id and t.usedAt is null and t.invalidatedAt is null""")
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);

    /** Thư mới thay cho mọi đường dẫn cũ chưa dùng của tài khoản. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update VerificationToken t set t.invalidatedAt = :now
            where t.userId = :userId and t.usedAt is null and t.invalidatedAt is null""")
    int invalidateOpenTokens(@Param("userId") UUID userId, @Param("now") Instant now);
}
