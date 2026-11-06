package com.learningcompanion.library;

import static org.assertj.core.api.Assertions.assertThat;

import com.learningcompanion.library.api.dto.DocumentChunkResponse;
import com.learningcompanion.library.api.dto.ViewerContentResponse;
import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.domain.DocumentStatus;
import com.learningcompanion.library.domain.DocumentType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ViewerContentResponseTest {
    @Test
    void readyDocumentHasFullProgressAndLocatedPassages() {
        Document document = Document.create(UUID.randomUUID(), "Bài giảng", DocumentType.PDF, "", "a.pdf",
                "application/pdf", 100, "stored", Instant.now());
        document.markProcessed("Đạo hàm riêng", 200, 1, "mock-primary", Instant.now());
        DocumentChunkResponse passage = new DocumentChunkResponse(1,
                new DocumentChunkResponse.Location("PAGE", 12, 12), null, "Đạo hàm riêng", "TEXT");
        ViewerContentResponse response = ViewerContentResponse.of(document, List.of(passage));
        assertThat(response.percent()).isEqualTo(100);
        assertThat(response.processing().units()).isEqualTo(200);
        assertThat(response.chunks()).containsExactly(passage);
    }

    @Test
    void pendingDocumentReportsZeroProgress() {
        Document document = Document.create(UUID.randomUUID(), "Đang chờ", DocumentType.TEXT, "",
                "a.txt", "text/plain", 10, "stored", Instant.now());
        document.changeStatus(DocumentStatus.PENDING);
        assertThat(ViewerContentResponse.of(document, List.of()).percent()).isZero();
    }
}
