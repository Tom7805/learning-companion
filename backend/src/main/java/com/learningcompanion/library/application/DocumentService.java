package com.learningcompanion.library.application;

import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.domain.DocumentRepository;
import com.learningcompanion.library.domain.DocumentType;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.LearningDataUsage;
import com.learningcompanion.shared.ownership.OwnerContext;
import com.learningcompanion.shared.ownership.OwnershipGuard;
import com.learningcompanion.shared.security.SignedUrlService;
import com.learningcompanion.shared.storage.FileStorageService;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đọc tài liệu và mở tệp gốc, luôn trong phạm vi chủ sở hữu của phiên hiện tại (NCL-01-CN-004). */
@Service
public class DocumentService implements LearningDataUsage {

    private final DocumentRepository documents;
    private final OwnerContext owner;
    private final OwnershipGuard guard;
    private final FileStorageService storage;
    private final SignedUrlService signedUrls;
    private final Clock clock;

    public DocumentService(DocumentRepository documents, OwnerContext owner, OwnershipGuard guard,
                           FileStorageService storage, SignedUrlService signedUrls, Clock clock) {
        this.documents = documents;
        this.owner = owner;
        this.guard = guard;
        this.storage = storage;
        this.signedUrls = signedUrls;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Document> list() {
        return documents.findAllByOwnerIdOrderByCreatedAtDesc(owner.requireOwnerId());
    }

    @Transactional(readOnly = true)
    public Document get(UUID id, ClientInfo client) {
        return guard.require(documents, id, AuditObjectType.DOCUMENT, client);
    }

    /** Đường dẫn mở tệp gốc, hết hạn sau năm phút; chỉ chủ sở hữu xin được. */
    @Transactional(readOnly = true)
    public SignedUrlService.SignedLink fileLink(UUID id, ClientInfo client) {
        Document document = guard.require(documents, id, AuditObjectType.DOCUMENT, client);
        return signedUrls.sign(document.getId(), document.getOwnerId());
    }

    /**
     * Mở tệp bằng đường dẫn ký. Tài liệu vẫn được tra kèm chủ sở hữu ghi trong chữ ký,
     * nên tài liệu đã xóa hoặc đổi chủ thì đường dẫn cũ không mở được nữa.
     */
    @Transactional(readOnly = true)
    public OpenedFile open(String token) {
        SignedUrlService.Claims claims = signedUrls.verify(token);
        Document document = documents.findByIdAndOwnerId(claims.objectId(), claims.ownerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return new OpenedFile(document, storage.read(document.getStorageKey()));
    }

    /** Tạo tài liệu cho người học đang đăng nhập; dùng bởi dữ liệu mẫu và sau này bởi chức năng tải lên. */
    @Transactional
    public Document create(UUID ownerId, String title, DocumentType type, String contentText,
                           String originalFilename, String mimeType, byte[] file) {
        String key = storage.store(ownerId, file);
        return documents.save(Document.create(ownerId, title, type, contentText, originalFilename, mimeType,
                file.length, key, clock.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public Usage usageOf(UUID ownerId) {
        return new Usage(documents.countByOwnerId(ownerId), documents.sumSizeBytesByOwnerId(ownerId));
    }

    public record OpenedFile(Document document, byte[] content) {
    }
}
