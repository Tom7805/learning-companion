package com.learningcompanion.workspace.domain;

import com.learningcompanion.shared.ownership.OwnerScopedRepository;
import java.util.List;
import java.util.UUID;

/** Chủ đề trong dàn ý, luôn tra kèm chủ sở hữu. */
public interface TopicRepository extends OwnerScopedRepository<Topic> {

    List<Topic> findAllByOwnerIdAndWorkspaceIdOrderByPositionAsc(UUID ownerId, UUID workspaceId);
}
