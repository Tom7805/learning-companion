package com.learningcompanion.library.domain;

import com.learningcompanion.shared.ownership.OwnerScopedRepository;

/** Phiên tải tệp lên, luôn tra kèm chủ sở hữu; xem {@link OwnerScopedRepository}. */
public interface UploadSessionRepository extends OwnerScopedRepository<UploadSession> {
}
