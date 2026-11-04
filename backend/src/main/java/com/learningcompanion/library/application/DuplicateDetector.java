package com.learningcompanion.library.application;

import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.domain.DocumentRepository;
import com.learningcompanion.library.domain.Folder;
import com.learningcompanion.library.domain.FolderRepository;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Báo tài liệu trùng nội dung ở bất kỳ đâu trong kho (QTN-17, NCL-05-CN-002 TC-03): so dấu vân tay SHA-256 của tệp với
 * mọi tài liệu còn hoạt động của người học, và chỉ ra vị trí của bản đã có để gợi ý dùng lại thay vì lưu thêm.
 */
@Component
public class DuplicateDetector {

    private final DocumentRepository documents;
    private final FolderRepository folders;

    public DuplicateDetector(DocumentRepository documents, FolderRepository folders) {
        this.documents = documents;
        this.folders = folders;
    }

    /** Bản đã có sớm nhất cùng nội dung, kèm đường dẫn thư mục từ gốc (rỗng: thư mục gốc). */
    @Transactional(readOnly = true)
    public Optional<Duplicate> find(UUID ownerId, String contentSha256) {
        return documents.findAllByOwnerIdAndContentSha256AndTrashedAtIsNullOrderByCreatedAtAsc(ownerId, contentSha256)
                .stream().findFirst()
                .map(existing -> new Duplicate(existing, folderPath(ownerId, existing.getFolderId())));
    }

    /** Ném {@link ErrorCode#LIBRARY_DOCUMENT_DUPLICATE} kèm mã, tên và vị trí của bản đã có. */
    @Transactional(readOnly = true)
    public void requireUnique(UUID ownerId, String contentSha256) {
        find(ownerId, contentSha256).ifPresent(duplicate -> {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("documentId", duplicate.document().getId());
            details.put("title", duplicate.document().getTitle());
            details.put("folderId", duplicate.document().getFolderId());
            details.put("folderPath", duplicate.folderPath());
            throw new BusinessException(ErrorCode.LIBRARY_DOCUMENT_DUPLICATE, null, details);
        });
    }

    private List<String> folderPath(UUID ownerId, UUID folderId) {
        if (folderId == null) {
            return List.of();
        }
        Map<UUID, Folder> byId = folders.findAllByOwnerIdAndTrashedAtIsNullOrderByNameAsc(ownerId).stream()
                .collect(Collectors.toMap(Folder::getId, Function.identity()));
        List<String> names = new ArrayList<>();
        Folder current = byId.get(folderId);
        while (current != null && names.size() <= byId.size()) {
            names.add(current.getName());
            current = current.getParentId() == null ? null : byId.get(current.getParentId());
        }
        Collections.reverse(names);
        return names;
    }

    public record Duplicate(Document document, List<String> folderPath) {
    }
}
