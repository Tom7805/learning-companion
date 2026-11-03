package com.learningcompanion.ai.config.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AiConfigVersionRepository extends JpaRepository<AiConfigVersion, UUID> {

    List<AiConfigVersion> findByFeatureOrderByVersionNumberDesc(AiFeature feature);

    Optional<AiConfigVersion> findByFeatureAndVersionNumber(AiFeature feature, int versionNumber);

    Optional<AiConfigVersion> findByFeatureAndStatus(AiFeature feature, ConfigVersionStatus status);

    boolean existsByFeature(AiFeature feature);

    @Query("select coalesce(max(v.versionNumber), 0) from AiConfigVersion v where v.feature = :feature")
    int maxVersionNumber(AiFeature feature);

    /**
     * Khóa mọi phiên bản của một chức năng trong giao dịch đang chạy, để hai thao tác lưu, phát hành hay quay lại cùng
     * lúc không cấp trùng số phiên bản hay để hai phiên bản cùng phát hành.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from AiConfigVersion v where v.feature = :feature order by v.versionNumber desc")
    List<AiConfigVersion> lockFeature(AiFeature feature);

    /** Phiên bản từng phát hành gần nhất trước phiên bản đang chạy: đích của thao tác quay lại. */
    @Query("select v from AiConfigVersion v where v.feature = :feature and v.status = "
            + "com.learningcompanion.ai.config.domain.ConfigVersionStatus.SUPERSEDED "
            + "and v.publishedAt is not null order by v.publishedAt desc, v.versionNumber desc")
    List<AiConfigVersion> previouslyPublished(AiFeature feature);
}
