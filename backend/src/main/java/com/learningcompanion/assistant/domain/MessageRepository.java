package com.learningcompanion.assistant.domain;

import com.learningcompanion.shared.ownership.OwnerScopedRepository;
import java.util.List;
import java.util.UUID;

/** Tin nhắn của hội thoại, luôn tra kèm chủ sở hữu. */
public interface MessageRepository extends OwnerScopedRepository<Message> {

    List<Message> findAllByOwnerIdAndConversationIdOrderByCreatedAtAsc(UUID ownerId, UUID conversationId);

    List<Message> findAllByOwnerIdOrderByCreatedAtAsc(UUID ownerId);
}
