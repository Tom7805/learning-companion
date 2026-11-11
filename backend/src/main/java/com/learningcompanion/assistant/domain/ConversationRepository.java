package com.learningcompanion.assistant.domain;

import com.learningcompanion.shared.ownership.OwnerScopedRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Hội thoại với trợ lý, luôn tra kèm chủ sở hữu; xem {@link OwnerScopedRepository}. */
public interface ConversationRepository extends OwnerScopedRepository<Conversation> {

    List<Conversation> findTop50ByOwnerIdAndWorkspaceIdAndTrashedAtIsNullOrderByUpdatedAtDesc(UUID ownerId,
                                                                                             UUID workspaceId);

    List<Conversation> findTop50ByOwnerIdAndWorkspaceIdIsNullAndTrashedAtIsNullOrderByUpdatedAtDesc(UUID ownerId);

    List<Conversation> findAllByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    /**
     * Lịch sử hội thoại riêng ở thanh bên (NCL-07-CN-003), đã ghim lên trước rồi mới dùng trước. Chỉ lấy hội thoại có
     * tên hay một tin nhắn khớp {@code pattern} (mẫu {@code like} viết thường, người gọi đã thoát ký tự đại diện bằng
     * {@code !}); không tìm thì truyền {@code %}.
     */
    @Query("select c from Conversation c where c.ownerId = :ownerId and c.trashedAt is null and c.workspaceId is null"
            + " and (lower(c.title) like :pattern escape '!' or exists (select 1 from Message m"
            + " where m.ownerId = :ownerId and m.conversationId = c.id and lower(m.content) like :pattern escape '!'))"
            + " order by case when c.pinnedAt is null then 1 else 0 end, c.pinnedAt desc, c.updatedAt desc")
    List<Conversation> privateHistoryByOwnerId(@Param("ownerId") UUID ownerId, @Param("pattern") String pattern,
                                               Pageable page);

    /** Như {@link #privateHistoryByOwnerId} nhưng cho hội thoại của một không gian học. */
    @Query("select c from Conversation c where c.ownerId = :ownerId and c.trashedAt is null"
            + " and c.workspaceId = :workspaceId"
            + " and (lower(c.title) like :pattern escape '!' or exists (select 1 from Message m"
            + " where m.ownerId = :ownerId and m.conversationId = c.id and lower(m.content) like :pattern escape '!'))"
            + " order by case when c.pinnedAt is null then 1 else 0 end, c.pinnedAt desc, c.updatedAt desc")
    List<Conversation> workspaceHistoryByOwnerId(@Param("ownerId") UUID ownerId,
                                                 @Param("workspaceId") UUID workspaceId,
                                                 @Param("pattern") String pattern, Pageable page);
}
