package com.learningcompanion.privacy.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequest, UUID> {

    Optional<AccountDeletionRequest> findByUserIdAndStatus(UUID userId, AccountDeletionRequest.Status status);

    /** Các yêu cầu đã tới hạn xóa, cũ nhất trước. */
    List<AccountDeletionRequest> findByStatusAndScheduledForLessThanEqualOrderByScheduledForAsc(
            AccountDeletionRequest.Status status, Instant now);
}
