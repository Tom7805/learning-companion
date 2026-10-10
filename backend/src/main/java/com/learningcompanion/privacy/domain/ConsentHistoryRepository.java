package com.learningcompanion.privacy.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentHistoryRepository extends JpaRepository<ConsentHistory, UUID> {

    List<ConsentHistory> findByUserIdOrderByRecordedAtAsc(UUID userId);
}
