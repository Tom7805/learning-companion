package com.learningcompanion.privacy.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, UUID> {

    List<Consent> findByUserId(UUID userId);

    Optional<Consent> findByUserIdAndPurpose(UUID userId, ConsentPurpose purpose);

    /** Người nhận của một xử lý không bắt buộc, ví dụ thư giới thiệu tính năng mới. */
    List<Consent> findByPurposeAndGrantedTrue(ConsentPurpose purpose);
}
