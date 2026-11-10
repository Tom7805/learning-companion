package com.learningcompanion.workspace.domain;

import com.learningcompanion.shared.ownership.OwnerScopedRepository;
import java.util.List;
import java.util.UUID;

/** Không gian học, luôn tra kèm chủ sở hữu; xem {@link OwnerScopedRepository}. */
public interface WorkspaceRepository extends OwnerScopedRepository<Workspace> {

    List<Workspace> findAllByOwnerIdAndTrashedAtIsNull(UUID ownerId);

    List<Workspace> findAllByOwnerIdOrderByCreatedAtAsc(UUID ownerId);
}
