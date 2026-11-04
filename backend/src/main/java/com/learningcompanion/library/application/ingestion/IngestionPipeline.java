package com.learningcompanion.library.application.ingestion;

import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.domain.DocumentChunk;
import com.learningcompanion.library.domain.DocumentChunkRepository;
import com.learningcompanion.library.domain.DocumentRepository;
import com.learningcompanion.library.domain.DocumentStatus;
import com.learningcompanion.library.domain.ProcessingError;
import com.learningcompanion.library.infrastructure.extractor.Extraction;
import com.learningcompanion.library.infrastructure.extractor.UnreadableDocumentException;
import com.learningcompanion.notification.application.NotificationService;
import com.learningcompanion.notification.domain.NotificationTargetType;
import com.learningcompanion.notification.domain.NotificationType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.storage.FileStorageService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Xử lý một tài liệu (NCL-05-CN-003): nhận tài liệu chờ xử lý, trích văn bản theo loại tệp (trang quét và ảnh qua nhận
 * dạng chữ), chia đoạn theo đề mục và độ dài, tạo biểu diễn ngữ nghĩa cho từng đoạn, rồi lưu các đoạn tách theo chủ sở
 * hữu và chuyển tài liệu sang sẵn sàng; lỗi thì chuyển sang lỗi kèm lý do. Người học nhận thông báo trong cả hai
 * trường hợp. Việc xử lý không đi qua sổ lượt: tài liệu của người học không tốn lượt trí tuệ nhân tạo của họ.
 *
 * <p>Phần trích, nhận dạng và tạo vector chạy ngoài giao dịch vì có thể mất vài chục giây; chỉ bước nhận tài liệu và
 * bước lưu kết quả nằm trong giao dịch ngắn.
 */
@Service
public class IngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);

    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final FileStorageService storage;
    private final TextExtractionService extraction;
    private final OcrService ocr;
    private final ChunkingService chunking;
    private final EmbeddingIndexer indexer;
    private final NotificationService notifications;
    private final ProcessingProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;

    public IngestionPipeline(DocumentRepository documents, DocumentChunkRepository chunks,
                             FileStorageService storage, TextExtractionService extraction, OcrService ocr,
                             ChunkingService chunking, EmbeddingIndexer indexer, NotificationService notifications,
                             ProcessingProperties properties, PlatformTransactionManager transactions, Clock clock) {
        this.documents = documents;
        this.chunks = chunks;
        this.storage = storage;
        this.extraction = extraction;
        this.ocr = ocr;
        this.chunking = chunking;
        this.indexer = indexer;
        this.notifications = notifications;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /** Xử lý tài liệu nếu nhận được nó; trả về {@code false} khi tài liệu đã được xử lý hay đang được xử lý ở nơi khác. */
    public boolean process(DocumentQueued job) {
        Document document = claim(job).orElse(null);
        if (document == null) {
            return false;
        }
        try {
            if (!extraction.supports(document.getType())) {
                fail(job, ProcessingError.UNSUPPORTED, null);
                return true;
            }
            byte[] file = storage.read(document.getStorageKey());
            Extraction extracted = extraction.extract(document.getType(), file, document.getMimeType(), ocr::recognize)
                    .orElseThrow();
            List<ChunkingService.Draft> drafts = chunking.chunk(extracted.blocks());
            if (drafts.isEmpty()) {
                // Trang trắng hoặc ảnh không có chữ: gợi ý tải bản rõ hơn (TC-04).
                fail(job, ProcessingError.NO_TEXT, null);
                return true;
            }
            EmbeddingIndexer.Embeddings embeddings = indexer.embed(drafts);
            succeed(job, extracted, drafts, embeddings);
        } catch (OcrService.PageRecognitionException ex) {
            fail(job, ProcessingError.OCR_FAILED, ex.page());
        } catch (UnreadableDocumentException ex) {
            log.info("Tài liệu {} không đọc được: {}", job.documentId(), ex.getMessage());
            fail(job, ProcessingError.UNREADABLE, null);
        } catch (BusinessException ex) {
            if (ex.code() != ErrorCode.AI_TEMPORARILY_UNAVAILABLE) {
                log.warn("Xử lý tài liệu {} lỗi: {}", job.documentId(), ex.code());
            }
            fail(job, ex.code() == ErrorCode.AI_TEMPORARILY_UNAVAILABLE ? ProcessingError.INDEXING_FAILED
                    : ProcessingError.INTERNAL, null);
        } catch (RuntimeException ex) {
            log.warn("Xử lý tài liệu {} lỗi không lường trước", job.documentId(), ex);
            fail(job, ProcessingError.INTERNAL, null);
        }
        return true;
    }

    private Optional<Document> claim(DocumentQueued job) {
        return tx.execute(status -> {
            Instant now = clock.instant();
            int claimed = documents.claimForProcessingByIdAndOwnerId(job.documentId(), job.ownerId(), now,
                    now.minus(properties.staleAfter()), DocumentStatus.PENDING, DocumentStatus.PROCESSING);
            if (claimed == 0) {
                return Optional.<Document>empty();
            }
            return documents.findByIdAndOwnerId(job.documentId(), job.ownerId());
        });
    }

    private void succeed(DocumentQueued job, Extraction extracted, List<ChunkingService.Draft> drafts,
                         EmbeddingIndexer.Embeddings embeddings) {
        tx.executeWithoutResult(status -> {
            Document document = current(job).orElse(null);
            if (document == null) {
                return;
            }
            Instant now = clock.instant();
            chunks.deleteByOwnerIdAndDocumentId(document.getOwnerId(), document.getId());
            List<DocumentChunk> rows = new ArrayList<>(drafts.size());
            for (int i = 0; i < drafts.size(); i++) {
                ChunkingService.Draft draft = drafts.get(i);
                rows.add(DocumentChunk.create(document, i + 1, draft.location(), draft.heading(), draft.content(),
                        draft.source(), embeddings.vectors().get(i), now));
            }
            rows.forEach(chunks::save);
            document.markProcessed(extracted.fullText(), extracted.unitCount(), rows.size(),
                    embeddings.providerCode(), now);
            documents.save(document);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("title", document.getTitle());
            params.put("units", extracted.unitCount());
            params.put("documentType", document.getType().name());
            params.put("chunks", rows.size());
            notifications.notify(document.getOwnerId(), NotificationType.DOCUMENT_READY,
                    NotificationTargetType.DOCUMENT, document.getId(), params,
                    "document-ready:" + document.getId() + ":" + document.getProcessingAttempts());
        });
    }

    private void fail(DocumentQueued job, ProcessingError error, Integer page) {
        tx.executeWithoutResult(status -> {
            Document document = current(job).orElse(null);
            if (document == null) {
                return;
            }
            document.markFailed(error, page, clock.instant());
            documents.save(document);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("title", document.getTitle());
            params.put("error", error.name());
            if (page != null) {
                params.put("page", page);
            }
            notifications.notify(document.getOwnerId(), NotificationType.DOCUMENT_PROCESSING_FAILED,
                    NotificationTargetType.DOCUMENT, document.getId(), params,
                    "document-failed:" + document.getId() + ":" + document.getProcessingAttempts());
        });
    }

    /** Tài liệu vẫn đang được lần xử lý này giữ: chưa vào thùng rác, chưa bị nhận lại ở nơi khác. */
    private Optional<Document> current(DocumentQueued job) {
        return documents.findByIdAndOwnerId(job.documentId(), job.ownerId())
                .filter(document -> !document.isTrashed() && document.getStatus() == DocumentStatus.PROCESSING);
    }
}
