package com.learningcompanion.privacy.domain;

import com.learningcompanion.shared.ownership.OwnerScopedRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Mọi truy vấn lần xuất đều kèm chủ sở hữu; xem {@link OwnerScopedRepository}. */
public interface DataExportRequestRepository extends OwnerScopedRepository<DataExportRequest> {

    Optional<DataExportRequest> findFirstByOwnerIdOrderByRequestedAtDesc(UUID ownerId);

    /** Lần yêu cầu gần nhất còn tính vào giới hạn mỗi ngày (lần thất bại không tính). */
    Optional<DataExportRequest> findFirstByOwnerIdAndStatusNotOrderByRequestedAtDesc(UUID ownerId,
                                                                                    DataExportStatus status);

    List<DataExportRequest> findAllByOwnerIdAndStatus(UUID ownerId, DataExportStatus status);
}
