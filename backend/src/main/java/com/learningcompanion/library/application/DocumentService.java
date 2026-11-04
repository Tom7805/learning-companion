package com.learningcompanion.library.application;

import com.learningcompanion.ai.quota.application.StorageQuotaService;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tài liệu trong kho tài liệu của người học: đọc, lọc, đổi tên, chuyển thư mục, gắn nhãn, gắn sao và đưa vào thùng
 * rác (NCL-01-CN-004, NCL-05-CN-001). Luôn trong phạm vi chủ sở hữu của phiên hiện tại; tài liệu trong thùng rác được
 * trả lời như không tồn tại.
 */
@Service
public class DocumentService implements LearningDataUsage {

    public static final int TITLE_MAX_LENGTH = 200;

    private final DocumentRepository documents;
    private final DocumentSearch search;
    private final FolderService folders;
    private final TagService tags;
    private final OwnerContext owner;
    private final OwnershipGuard guard;
    private final FileStorageService storage;
    private final SignedUrlService signedUrls;
    private final StorageQuotaService storageQuota;
    private final Clock clock;

    public DocumentService(DocumentRepository documents, DocumentSearch search, FolderService folders,
                           TagService tags, OwnerContext owner, OwnershipGuard guard, FileStorageService storage,
                           SignedUrlService signedUrls, StorageQuotaService storageQuota, Clock clock) {
        this.documents = documents;
        this.search = search;
        this.folders = folders;
        this.tags = tags;
        this.owner = owner;
        this.guard = guard;
        this.storage = storage;
        this.signedUrls = signedUrls;
        this.storageQuota = storageQuota;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Document> list(DocumentQuery query, ClientInfo client) {
        UUID ownerId = owner.requireOwnerId();
        if (query.scope() == DocumentQuery.Scope.FOLDER && query.folderId() != null) {
            // Mở thư mục của người khác bằng đường dẫn trực tiếp: 404 và ghi nhật ký (NCL-05-CN-001 TC-05).
            folders.require(query.folderId(), client);
        }
        return search.search(ownerId, query);
    }

    /** Mở một tài liệu: ghi lại lần mở gần nhất cho lối tắt "Gần đây". */
    @Transactional
    public Document open(UUID id, ClientInfo client) {
        Document document = require(id, client);
        document.markOpened(clock.instant());
        return document;
    }

    @Transactional(readOnly = true)
    public Document get(UUID id, ClientInfo client) {
        return require(id, client);
    }

    /** Đường dẫn mở tệp gốc, hết hạn sau năm phút; chỉ chủ sở hữu xin được. */
    @Transactional(readOnly = true)
    public SignedUrlService.SignedLink fileLink(UUID id, ClientInfo client) {
        Document document = require(id, client);
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
                .filter(found -> !found.isTrashed())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return new OpenedFile(document, storage.read(document.getStorageKey()));
    }

    /**
     * Tạo tài liệu cho người học đang đăng nhập; dùng bởi dữ liệu mẫu và sau này bởi chức năng tải lên. Tệp làm vượt
     * hạn mức dung lượng thì bị chặn trước khi ghi (NCL-04-CN-003).
     */
    @Transactional
    public Document create(UUID ownerId, String title, DocumentType type, String contentText,
                           String originalFilename, String mimeType, byte[] file) {
        storageQuota.requireRoom(ownerId, file.length);
        String key = storage.store(ownerId, file);
        return documents.save(Document.create(ownerId, title, type, contentText, originalFilename, mimeType,
                file.length, key, clock.instant()));
    }

    /** Đổi tên; tên trùng với tài liệu khác trong cùng thư mục bị báo kèm tên gợi ý (NCL-05-CN-001). */
    @Transactional
    public Document rename(UUID id, String title, ClientInfo client) {
        Document document = require(id, client);
        String clean = LibraryNames.clean(title);
        if (clean.isEmpty() || clean.length() > TITLE_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "title");
        }
        if (!clean.equals(document.getTitle())) {
            Set<String> taken = takenTitles(document.getOwnerId(), document.getFolderId(), Set.of(document.getId()));
            if (taken.contains(LibraryNames.key(clean))) {
                throw FolderService.nameTaken(LibraryNames.suggest(clean, taken, TITLE_MAX_LENGTH));
            }
            document.rename(clean, clock.instant());
        }
        return document;
    }

    @Transactional
    public Document star(UUID id, boolean starred, ClientInfo client) {
        Document document = require(id, client);
        document.star(starred);
        return document;
    }

    /** Thay toàn bộ nhãn của một tài liệu bằng danh sách mới. */
    @Transactional
    public Document setTags(UUID id, Collection<UUID> tagIds, ClientInfo client) {
        Document document = require(id, client);
        document.replaceTags(tags.requireOwned(document.getOwnerId(), tagIds));
        return document;
    }

    @Transactional
    public Document trash(UUID id, ClientInfo client) {
        Document document = require(id, client);
        document.trash(UUID.randomUUID(), clock.instant());
        return document;
    }

    /** Mọi tài liệu trong danh sách, đều phải là tài liệu còn hoạt động của người đang đăng nhập. */
    @Transactional(readOnly = true)
    public List<Document> requireAll(Collection<UUID> ids, ClientInfo client) {
        List<Document> found = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(ids)) {
            found.add(require(id, client));
        }
        return found;
    }

    /**
     * Chuyển các tài liệu vào một thư mục ({@code null}: thư mục gốc). Tài liệu trùng tên với tài liệu đang có ở thư mục
     * đích thì cả lần chuyển bị từ chối, chi tiết liệt kê tên trùng.
     */
    @Transactional
    public List<Document> move(Collection<UUID> ids, UUID folderId, ClientInfo client) {
        List<Document> moving = requireAll(ids, client);
        if (folderId != null) {
            folders.require(folderId, client);
        }
        UUID ownerId = owner.requireOwnerId();
        Set<UUID> movingIds = new LinkedHashSet<>(moving.stream().map(Document::getId).toList());
        Set<String> taken = takenTitles(ownerId, folderId, movingIds);
        List<String> conflicts = moving.stream()
                .filter(document -> !Objects.equals(document.getFolderId(), folderId))
                .map(Document::getTitle)
                .filter(title -> taken.contains(LibraryNames.key(title)))
                .toList();
        if (!conflicts.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("conflicts", conflicts);
            throw new BusinessException(ErrorCode.LIBRARY_NAME_TAKEN, "folderId", details);
        }
        Instant now = clock.instant();
        moving.stream().filter(document -> !Objects.equals(document.getFolderId(), folderId))
                .forEach(document -> document.moveTo(folderId, now));
        return moving;
    }

    @Override
    @Transactional(readOnly = true)
    public Usage usageOf(UUID ownerId) {
        return new Usage(documents.countByOwnerId(ownerId), documents.sumSizeBytesByOwnerId(ownerId));
    }

    private Document require(UUID id, ClientInfo client) {
        Document document = guard.require(documents, id, AuditObjectType.DOCUMENT, client);
        if (document.isTrashed()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return document;
    }

    private Set<String> takenTitles(UUID ownerId, UUID folderId, Set<UUID> except) {
        return LibraryNames.keys(folders.documentsIn(ownerId, folderId).stream()
                .filter(document -> !except.contains(document.getId())).toList(), Document::getTitle);
    }

    public record OpenedFile(Document document, byte[] content) {
    }
}
