package com.learningcompanion.auth.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OAuthFlowRepository extends JpaRepository<OAuthFlow, UUID> {

    Optional<OAuthFlow> findByStateHash(String stateHash);

    /** Đánh dấu đã dùng một cách nguyên tử: một mã chống giả mạo chỉ hoàn tất được đúng một lần. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update OAuthFlow f set f.consumedAt = :now where f.id = :id and f.consumedAt is null")
    int consume(@Param("id") UUID id, @Param("now") Instant now);
}
