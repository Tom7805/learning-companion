package com.learningcompanion.ai.labeling;

import com.learningcompanion.shared.ownership.OwnerScopedRepository;
import java.util.Optional;
import java.util.UUID;

/** Lần đọc thông báo trí tuệ nhân tạo, mỗi người học một dòng, chỉ đọc kèm chủ sở hữu. */
public interface AiDisclosureAcknowledgementRepository extends OwnerScopedRepository<AiDisclosureAcknowledgement> {

    Optional<AiDisclosureAcknowledgement> findByOwnerId(UUID ownerId);
}
