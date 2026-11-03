package com.learningcompanion.admin.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

    Optional<RecoveryCode> findByAccountIdAndCodeHash(UUID accountId, String codeHash);

    long countByAccountIdAndUsedAtIsNull(UUID accountId);

    @Modifying
    @Query("delete from RecoveryCode code where code.accountId = :accountId")
    void deleteAllOfAccount(UUID accountId);
}
