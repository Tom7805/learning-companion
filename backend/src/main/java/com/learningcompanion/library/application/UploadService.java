package com.learningcompanion.library.application;

import com.learningcompanion.ai.quota.application.StorageQuotaService;
import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.domain.DocumentRepository;
import com.learningcompanion.library.domain.DocumentType;
import com.learningcompanion.library.domain.UploadSession;
import com.learningcompanion.library.domain.UploadSessionRepository;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.OwnerContext;
import com.learningcompanion.shared.ownership.OwnershipGuard;
import com.learningcompanion.shared.storage.ChunkedUploadStore;
import com.learningcompanion.shared.storage.FileTypeDetector;
import com.learningcompanion.shared.storage.MalwareScanner;
import com.learningcompanion.shared.storage.UploadProperties;
import com.learningcompanion.shared.web.ClientInfo;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tải tài liệu lên kho (NCL-05-CN-002, QTN-19). Một lần tải đi qua ba bước:
 * <ol>
 * <li>bắt đầu: kiểm thư mục đích thuộc người học (của người khác thì 404 và ghi nhật ký, TC-05), dung lượng tối đa một
 * tệp, dung lượng còn lại, và báo trùng sớm theo dấu vân tay giao diện tự tính để khỏi gửi cả tệp;</li>
 * <li>gửi từng phần theo thứ tự: mất kết nối thì hỏi lại tiến độ và gửi tiếp từ phần còn thiếu (TC-04);</li>
 * <li>hoàn tất: ghép tệp, quét mã độc (TC-02), nhận dạng định dạng theo nội dung thật, từ chối tệp có mật khẩu, hỏng hay
 * bung ra quá lớn, tính lại dấu vân tay để báo trùng ở bất kỳ đâu trong kho (TC-03), rồi lưu tệp mã hóa và tạo tài
 * liệu chờ xử lý trong thư mục đích (TC-01).</li>
 * </ol>
 * Mọi lần từ chối đều bỏ hẳn các phần đã nhận và được ghi nhật ký kèm lý do.
 */
@Service
public class UploadService {

    public static final int FILE_NAME_MAX_LENGTH = 255;

    private final UploadSessionRepository sessions;
    private final DocumentRepository documents;
    private final DuplicateDetector duplicates;
    private final DocumentService documentService;
    private final FolderService folders;
    private final OwnerContext owner;
    private final OwnershipGuard guard;
    private final ChunkedUploadStore parts;
    private final FileTypeDetector detector;
    private final MalwareScanner scanner;
    private final StorageQuotaService storageQuota;
    private final UploadRejections rejections;
    private final UploadProperties properties;
    private final Clock clock;

    public UploadService(UploadSessionRepository sessions, DocumentRepository documents,
                         DuplicateDetector duplicates, DocumentService documentService, FolderService folders,
                         OwnerContext owner, OwnershipGuard guard, ChunkedUploadStore parts,
                         FileTypeDetector detector, MalwareScanner scanner, StorageQuotaService storageQuota,
                         UploadRejections rejections, UploadProperties properties, Clock clock) {
        this.sessions = sessions;
        this.documents = documents;
        this.duplicates = duplicates;
        this.documentService = documentService;
        this.folders = folders;
        this.owner = owner;
        this.guard = guard;
        this.parts = parts;
        this.detector = detector;
        this.scanner = scanner;
        this.storageQuota = storageQuota;
        this.rejections = rejections;
        this.properties = properties;
        this.clock = clock;
    }

    /** Bắt đầu một lần tải; {@code contentSha256} (có thể trống) là dấu vân tay giao diện tính trước để báo trùng sớm. */
    @Transactional
    public UploadSession start(String fileName, long sizeBytes, UUID folderId, String contentSha256,
                               ClientInfo client) {
        UUID ownerId = owner.requireOwnerId();
        if (folderId != null) {
            folders.require(folderId, client);
        }
        String name = cleanFileName(fileName);
        if (name.isEmpty() || sizeBytes <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, name.isEmpty() ? "fileName" : "sizeBytes");
        }
        requireFileSize(ownerId, null, sizeBytes, client);
        requireRoom(ownerId, null, sizeBytes, client);
        if (contentSha256 != null) {
            duplicates.requireUnique(ownerId, contentSha256.toLowerCase(Locale.ROOT));
        }
        Instant now = clock.instant();
        return sessions.save(UploadSession.start(ownerId, folderId, name, sizeBytes, properties.chunkSizeBytes(), now,
                now.plus(properties.sessionTtl())));
    }

    @Transactional(readOnly = true)
    public UploadSession get(UUID id, ClientInfo client) {
        return require(id, client);
    }

    /**
     * Nhận phần thứ {@code index}. Phần phải đúng cỡ và không vượt lên trước phần còn thiếu; gửi lại phần đã có (vì mất
     * kết nối trước khi nhận được trả lời) chỉ ghi đè, không làm sai tiến độ.
     */
    @Transactional
    public UploadSession receivePart(UUID id, int index, byte[] content, ClientInfo client) {
        UploadSession session = require(id, client);
        if (session.getStatus() == UploadSession.Status.COMPLETED) {
            return session;
        }
        if (index < 0 || index >= session.partCount()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "index");
        }
        if (index > session.nextPart()) {
            throw outOfOrder(session);
        }
        if (content.length != session.expectedLength(index)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "part");
        }
        parts.writePart(session.getOwnerId(), session.getId(), index, content);
        Instant now = clock.instant();
        session.acceptPart(index, now, now.plus(properties.sessionTtl()));
        return session;
    }

    /**
     * Ghép tệp, kiểm tra và tạo tài liệu. Gửi lại yêu cầu hoàn tất của lần tải đã xong trả về đúng tài liệu đã tạo,
     * không tạo bản thứ hai.
     */
    @Transactional
    public Document complete(UUID id, ClientInfo client) {
        UploadSession session = require(id, client);
        UUID ownerId = session.getOwnerId();
        if (session.getStatus() == UploadSession.Status.COMPLETED) {
            return documents.findByIdAndOwnerId(session.getDocumentId(), ownerId)
                    .filter(document -> !document.isTrashed())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        }
        if (!session.isFullyReceived()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("receivedBytes", session.getReceivedBytes());
            details.put("nextPart", session.nextPart());
            throw new BusinessException(ErrorCode.UPLOAD_INCOMPLETE, null, details);
        }
        byte[] file = parts.assemble(ownerId, session.getId(), session.partCount());
        MalwareScanner.Result scan;
        try {
            scan = scanner.scan(file);
        } catch (MalwareScanner.ScanUnavailableException ex) {
            // Giữ các phần đã nhận để người học bấm thử lại mà không phải tải lại tệp.
            throw new BusinessException(ErrorCode.MALWARE_SCAN_UNAVAILABLE);
        }
        if (scan.infected()) {
            throw rejected(session, "MALWARE", file.length, client);
        }
        FileTypeDetector.Inspection inspection = detector.inspect(file);
        if (!inspection.accepted()) {
            throw rejected(session, inspection.problem().name(), file.length, client);
        }
        try {
            requireFileSize(ownerId, session.getId(), file.length, client);
            requireRoom(ownerId, session.getId(), file.length, client);
            String fingerprint = Fingerprints.sha256(file);
            duplicates.requireUnique(ownerId, fingerprint);
            if (session.getFolderId() != null) {
                // Thư mục đích có thể đã vào thùng rác trong lúc tải.
                folders.require(session.getFolderId(), client);
            }
            Document document = documentService.createUploaded(ownerId, session.getFolderId(),
                    titleOf(session.getFileName()), typeOf(inspection.kind()), session.getFileName(),
                    mimeTypeOf(inspection.kind(), session.getFileName()), file, fingerprint);
            session.complete(document.getId(), clock.instant());
            parts.discard(ownerId, session.getId());
            return document;
        } catch (BusinessException ex) {
            // Trùng hay thư mục đích không còn: bỏ lần tải, tệp không được lưu. Quá cỡ và hết dung lượng đã được bỏ
            // cùng lúc ghi nhật ký.
            rejections.discard(ownerId, session.getId());
            throw ex;
        }
    }

    /** Hủy một lần tải đang dở: bỏ mọi phần đã nhận. */
    @Transactional
    public void cancel(UUID id, ClientInfo client) {
        UploadSession session = require(id, client);
        if (session.getStatus() == UploadSession.Status.UPLOADING) {
            parts.discard(session.getOwnerId(), session.getId());
        }
        sessions.delete(session);
    }

    private UploadSession require(UUID id, ClientInfo client) {
        return guard.require(sessions, id, AuditObjectType.LIBRARY_UPLOAD, client);
    }

    private void requireFileSize(UUID ownerId, UUID uploadId, long sizeBytes, ClientInfo client) {
        try {
            storageQuota.requireFileSize(sizeBytes);
        } catch (BusinessException ex) {
            rejections.reject(ownerId, uploadId, "TOO_LARGE", sizeBytes, client);
            throw ex;
        }
    }

    private void requireRoom(UUID ownerId, UUID uploadId, long sizeBytes, ClientInfo client) {
        try {
            storageQuota.requireRoom(ownerId, sizeBytes);
        } catch (BusinessException ex) {
            rejections.reject(ownerId, uploadId, "STORAGE_FULL", sizeBytes, client);
            throw ex;
        }
    }

    private BusinessException rejected(UploadSession session, String reason, long sizeBytes, ClientInfo client) {
        rejections.reject(session.getOwnerId(), session.getId(), reason, sizeBytes, client);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        return new BusinessException(ErrorCode.UPLOAD_REJECTED, null, details);
    }

    private static BusinessException outOfOrder(UploadSession session) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("receivedBytes", session.getReceivedBytes());
        details.put("nextPart", session.nextPart());
        return new BusinessException(ErrorCode.UPLOAD_PART_OUT_OF_ORDER, null, details);
    }

    /** Tên tệp do trình duyệt gửi: bỏ phần đường dẫn, ký tự điều khiển và khoảng trắng thừa. */
    static String cleanFileName(String raw) {
        if (raw == null) {
            return "";
        }
        String base = raw.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "");
        String clean = LibraryNames.clean(base);
        return clean.length() > FILE_NAME_MAX_LENGTH ? clean.substring(0, FILE_NAME_MAX_LENGTH) : clean;
    }

    /** Tên tài liệu mặc định: tên tệp bỏ đuôi. */
    static String titleOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String title = LibraryNames.clean(dot > 0 ? fileName.substring(0, dot) : fileName);
        if (title.isEmpty()) {
            title = LibraryNames.clean(fileName);
        }
        return title.length() > DocumentService.TITLE_MAX_LENGTH
                ? title.substring(0, DocumentService.TITLE_MAX_LENGTH).strip() : title;
    }

    static DocumentType typeOf(FileTypeDetector.FileKind kind) {
        return switch (kind) {
            case PDF -> DocumentType.PDF;
            case DOCX -> DocumentType.DOCX;
            case PPTX -> DocumentType.PPTX;
            case EPUB -> DocumentType.EPUB;
            case TEXT -> DocumentType.TEXT;
            case JPEG, PNG, WEBP -> DocumentType.IMAGE;
        };
    }

    static String mimeTypeOf(FileTypeDetector.FileKind kind, String fileName) {
        if (kind == FileTypeDetector.FileKind.TEXT && fileName.toLowerCase(Locale.ROOT).endsWith(".md")) {
            return "text/markdown; charset=utf-8";
        }
        return kind.mimeType();
    }
}
