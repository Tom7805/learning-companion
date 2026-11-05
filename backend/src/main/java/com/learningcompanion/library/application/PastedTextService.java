package com.learningcompanion.library.application;

import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.domain.DocumentType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.OwnerContext;
import com.learningcompanion.shared.web.ClientInfo;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Tạo tài liệu văn bản từ đoạn người học dán vào kèm tiêu đề (NCL-05-CN-004 TC-02). Văn bản được lưu như một tệp văn bản
 * thuần UTF-8 rồi đi qua đúng luồng của tệp tải lên: báo trùng, kiểm dung lượng, lưu mã hóa, trích văn bản và chia
 * đoạn theo dòng trống, giữ đúng thứ tự đoạn văn.
 */
@Service
public class PastedTextService {

    /** Đủ cho một chương giáo trình dài; văn bản dài hơn nên tải lên dạng tệp. */
    public static final int TEXT_MAX_LENGTH = 200_000;

    private final OwnerContext owner;
    private final FolderService folders;
    private final DuplicateDetector duplicates;
    private final DocumentService documentService;

    public PastedTextService(OwnerContext owner, FolderService folders, DuplicateDetector duplicates,
                             DocumentService documentService) {
        this.owner = owner;
        this.folders = folders;
        this.duplicates = duplicates;
        this.documentService = documentService;
    }

    public Document create(String rawTitle, String rawText, UUID folderId, ClientInfo client) {
        UUID ownerId = owner.requireOwnerId();
        if (folderId != null) {
            folders.require(folderId, client);
        }
        String title = LibraryNames.clean(rawTitle == null ? "" : rawTitle);
        if (title.isEmpty() || title.length() > DocumentService.TITLE_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "title");
        }
        String text = normalize(rawText);
        if (text.isEmpty() || text.length() > TEXT_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "text");
        }
        byte[] file = text.getBytes(StandardCharsets.UTF_8);
        String fingerprint = Fingerprints.sha256(file);
        duplicates.requireUnique(ownerId, fingerprint);
        return documentService.createUploaded(ownerId, folderId, title, DocumentType.TEXT,
                UploadService.cleanFileName(title + ".txt"), "text/plain; charset=utf-8", file, fingerprint);
    }

    /** Thống nhất xuống dòng, bỏ ký tự điều khiển (trừ tab và xuống dòng) và khoảng trắng thừa ở hai đầu. */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
                .strip();
        return text.isEmpty() ? "" : text + "\n";
    }
}
