package com.learningcompanion.library.domain;

import com.learningcompanion.shared.ownership.OwnerScopedRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Nhãn của kho tài liệu, luôn tra kèm chủ sở hữu. */
public interface TagRepository extends OwnerScopedRepository<Tag> {

    List<Tag> findAllByOwnerIdOrderByNameAsc(UUID ownerId);

    List<Tag> findAllByOwnerIdAndIdIn(UUID ownerId, Collection<UUID> ids);
}
