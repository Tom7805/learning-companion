package com.learningcompanion.library.application;

import com.learningcompanion.library.domain.DocumentRepository;
import com.learningcompanion.notification.application.NotificationTargetResolver;
import com.learningcompanion.notification.domain.NotificationTargetType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Thông báo về một tài liệu mở trang tài liệu; tài liệu đã vào thùng rác thì mở trang "nội dung không còn". */
@Component
public class DocumentNotificationTarget implements NotificationTargetResolver {

    private final DocumentRepository documents;

    public DocumentNotificationTarget(DocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    public NotificationTargetType type() {
        return NotificationTargetType.DOCUMENT;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> pathOf(UUID ownerId, UUID targetId) {
        if (targetId == null) {
            return Optional.empty();
        }
        return documents.findByIdAndOwnerId(targetId, ownerId)
                .filter(document -> !document.isTrashed())
                .map(document -> "/library/" + document.getId());
    }
}
