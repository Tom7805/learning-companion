package com.learningcompanion.auth.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Optional<PasswordResetToken> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Đánh dấu đã dùng một cách nguyên tử: bấm hai lần cùng lúc thì chỉ một lần đặt được mật khẩu. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PasswordResetToken t set t.usedAt = :now
            where t.id = :id and t.usedAt is null and t.invalidatedAt is null""")
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);

    /** Thư mới, hoặc mật khẩu vừa đổi, làm mọi đường dẫn cũ chưa dùng mất hiệu lực. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PasswordResetToken t set t.invalidatedAt = :now
            where t.userId = :userId and t.usedAt is null and t.invalidatedAt is null""")
    int invalidateOpenTokens(@Param("userId") UUID userId, @Param("now") Instant now);
}
