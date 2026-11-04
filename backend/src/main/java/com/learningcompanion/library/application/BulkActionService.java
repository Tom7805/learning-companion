package com.learningcompanion.library.application;

import com.learningcompanion.library.domain.Document;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.OwnerContext;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thao tác trên nhiều tài liệu cùng lúc (NCL-05-CN-001): chuyển thư mục, gắn hay gỡ nhãn, gắn sao, đưa vào thùng rác.
 * Cả lô chạy trong một giao dịch: chỉ cần một tài liệu không thuộc người học là cả lô bị từ chối, không đổi gì.
 */
@Service
public class BulkActionService {

    private final DocumentService documents;
    private final TagService tags;
    private final OwnerContext owner;
    private final Clock clock;

    public BulkActionService(DocumentService documents, TagService tags, OwnerContext owner, Clock clock) {
        this.documents = documents;
        this.tags = tags;
        this.owner = owner;
        this.clock = clock;
    }

    @Transactional
    public Result apply(Action action, Collection<UUID> documentIds, UUID folderId, Collection<UUID> tagIds,
                        ClientInfo client) {
        if (action == null || documentIds == null || documentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "documentIds");
        }
        if (action == Action.MOVE) {
            return new Result(action, documents.move(documentIds, folderId, client).size(), null);
        }
        List<Document> selected = documents.requireAll(documentIds, client);
        Instant now = clock.instant();
        UUID batch = null;
        switch (action) {
            case ADD_TAGS, REMOVE_TAGS -> {
                Set<UUID> owned = tags.requireOwned(owner.requireOwnerId(), tagIds == null ? List.of() : tagIds);
                if (owned.isEmpty()) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "tagIds");
                }
                selected.forEach(document -> {
                    if (action == Action.ADD_TAGS) {
                        document.addTags(owned);
                    } else {
                        document.removeTags(owned);
                    }
                });
            }
            case STAR, UNSTAR -> selected.forEach(document -> document.star(action == Action.STAR));
            case TRASH -> {
                // Một đợt xóa chung để thùng rác khôi phục được cả lô một lần.
                batch = UUID.randomUUID();
                UUID shared = batch;
                selected.forEach(document -> document.trash(shared, now));
            }
            case MOVE -> throw new IllegalStateException();
        }
        return new Result(action, selected.size(), batch);
    }

    public enum Action { MOVE, ADD_TAGS, REMOVE_TAGS, STAR, UNSTAR, TRASH }

    public record Result(Action action, int affected, UUID trashBatchId) {
    }
}
