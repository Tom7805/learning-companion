package com.learningcompanion.library.application;

import com.learningcompanion.library.api.dto.DocumentChunkResponse;
import com.learningcompanion.library.api.dto.ViewerContentResponse;
import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.domain.DocumentChunkRepository;
import com.learningcompanion.library.domain.DocumentStatus;
import com.learningcompanion.shared.web.ClientInfo;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nội dung khung xem; DocumentService áp dụng OwnershipGuard trước khi đọc các đoạn. */
@Service
public class ViewerService {
    private final DocumentService documents;
    private final DocumentChunkRepository chunks;

    public ViewerService(DocumentService documents, DocumentChunkRepository chunks) {
        this.documents = documents;
        this.chunks = chunks;
    }

    @Transactional
    public ViewerContentResponse open(UUID id, ClientInfo client) {
        Document document = documents.open(id, client);
        List<DocumentChunkResponse> passages = document.getStatus() == DocumentStatus.READY
                ? chunks.findAllByOwnerIdAndDocumentIdOrderByOrdinalAsc(document.getOwnerId(), id)
                        .stream().map(DocumentChunkResponse::of).toList()
                : List.of();
        if (passages.isEmpty() && document.getStatus() == DocumentStatus.READY
                && !document.getContentText().isBlank()) {
            passages = List.of(new DocumentChunkResponse(1, new DocumentChunkResponse.Location("PARAGRAPH", 1, 1),
                    null, document.getContentText(), "TEXT"));
        }
        // Nội dung của tài liệu chưa sẵn sàng không được gửi tới trình duyệt.
        return ViewerContentResponse.of(document, passages);
    }
}
