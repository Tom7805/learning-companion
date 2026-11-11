package com.learningcompanion.assistant.domain;

import com.learningcompanion.shared.ownership.OwnerScopedRepository;
import java.util.List;
import java.util.UUID;

/** Hội thoại với trợ lý, luôn tra kèm chủ sở hữu; xem {@link OwnerScopedRepository}. */
public interface ConversationRepository extends OwnerScopedRepository<Conversation> {

    List<Conversation> findTop50ByOwnerIdAndWorkspaceIdAndTrashedAtIsNullOrderByUpdatedAtDesc(UUID ownerId,
                                                                                             UUID workspaceId);

    List<Conversation> findTop50ByOwnerIdAndWorkspaceIdIsNullAndTrashedAtIsNullOrderByUpdatedAtDesc(UUID ownerId);

    List<Conversation> findAllByOwnerIdOrderByCreatedAtAsc(UUID ownerId);
}
