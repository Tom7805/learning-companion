package com.learningcompanion.library.application;

import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.domain.DocumentRepository;
import com.learningcompanion.library.domain.Folder;
import com.learningcompanion.library.domain.FolderRepository;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.OwnerContext;
import com.learningcompanion.shared.ownership.OwnershipGuard;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cây thư mục của kho tài liệu (NCL-05-CN-001): tạo, đổi tên, chuyển và xóa thư mục, không giới hạn số thư mục hay số
 * cấp. Thư mục của người khác, hay đã vào thùng rác, được trả lời như không tồn tại.
 */
@Service
public class FolderService {

    private final FolderRepository folders;
    private final DocumentRepository documents;
    private final OwnerContext owner;
    private final OwnershipGuard guard;
    private final Clock clock;
    private final EntityManager entityManager;

    public FolderService(FolderRepository folders, DocumentRepository documents, OwnerContext owner,
                         OwnershipGuard guard, Clock clock, EntityManager entityManager) {
        this.folders = folders;
        this.documents = documents;
        this.owner = owner;
        this.guard = guard;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    /** Toàn bộ cây (danh sách phẳng kèm thư mục cha) cùng số tài liệu nằm trực tiếp trong từng thư mục. */
    @Transactional(readOnly = true)
    public Tree tree() {
        UUID ownerId = owner.requireOwnerId();
        Map<UUID, Long> counts = new HashMap<>();
        long root = 0;
        for (Object[] row : documents.countActiveByOwnerIdGroupedByFolder(ownerId)) {
            long count = ((Number) row[1]).longValue();
            if (row[0] == null) {
                root = count;
            } else {
                counts.put((UUID) row[0], count);
            }
        }
        List<Node> nodes = folders.findAllByOwnerIdAndTrashedAtIsNullOrderByNameAsc(ownerId).stream()
                .map(folder -> new Node(folder, counts.getOrDefault(folder.getId(), 0L)))
                .toList();
        return new Tree(nodes, root, documents.countByOwnerIdAndTrashedAtIsNull(ownerId),
                documents.countByOwnerIdAndStarredTrueAndTrashedAtIsNull(ownerId));
    }

    /** Thư mục đang hoạt động của người đang đăng nhập; của người khác thì 404 và lần từ chối được ghi nhật ký. */
    @Transactional(readOnly = true)
    public Folder require(UUID id, ClientInfo client) {
        Folder folder = guard.require(folders, id, AuditObjectType.LIBRARY_FOLDER, client);
        if (folder.isTrashed()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return folder;
    }

    /** Thư mục đang hoạt động của một người học, nếu còn; dùng khi thư mục có thể đã bị xóa mà không phải lỗi. */
    @Transactional(readOnly = true)
    public Optional<Folder> findActive(UUID ownerId, UUID id) {
        return folders.findByIdAndOwnerId(id, ownerId).filter(folder -> !folder.isTrashed());
    }

    /** Mã mọi tài liệu còn hoạt động trong thư mục và các thư mục con cháu: những gì sẽ vào thùng rác cùng thư mục. */
    @Transactional(readOnly = true)
    public List<UUID> activeDocumentIdsUnder(UUID id, ClientInfo client) {
        Folder folder = require(id, client);
        return documents.findActiveIdsByOwnerIdAndFolderIdIn(folder.getOwnerId(),
                descendantsOf(folder.getOwnerId(), folder.getId()));
    }

    /** Kiểm tra tên trước khi lưu, để giao diện báo trùng ngay khi đang gõ. */
    @Transactional(readOnly = true)
    public NameCheck checkName(UUID parentId, String name, UUID exceptFolderId, ClientInfo client) {
        UUID ownerId = owner.requireOwnerId();
        if (parentId != null) {
            require(parentId, client);
        }
        Set<String> taken = takenKeys(ownerId, parentId, exceptFolderId);
        String clean = LibraryNames.clean(name);
        boolean available = clean.isEmpty() || !taken.contains(LibraryNames.key(clean));
        return new NameCheck(available, available ? null : LibraryNames.suggest(clean, taken, Folder.NAME_MAX_LENGTH));
    }

    @Transactional
    public Folder create(UUID parentId, String name, ClientInfo client) {
        UUID ownerId = owner.requireOwnerId();
        if (parentId != null) {
            require(parentId, client);
        }
        String clean = requireName(name);
        requireFree(ownerId, parentId, clean, null);
        return save(Folder.create(ownerId, parentId, clean, clock.instant()));
    }

    @Transactional
    public Folder rename(UUID id, String name, ClientInfo client) {
        Folder folder = require(id, client);
        String clean = requireName(name);
        if (!clean.equals(folder.getName())) {
            requireFree(folder.getOwnerId(), folder.getParentId(), clean, folder.getId());
            folder.rename(clean, clock.instant());
        }
        return save(folder);
    }

    /** Chuyển thư mục sang thư mục cha khác ({@code null}: thư mục gốc); không được chuyển vào chính nó hay con của nó. */
    @Transactional
    public Folder move(UUID id, UUID newParentId, ClientInfo client) {
        Folder folder = require(id, client);
        if (Objects.equals(folder.getParentId(), newParentId)) {
            return folder;
        }
        if (newParentId != null) {
            require(newParentId, client);
            if (descendantsOf(folder.getOwnerId(), folder.getId()).contains(newParentId)) {
                throw new BusinessException(ErrorCode.LIBRARY_FOLDER_CYCLE);
            }
        }
        requireFree(folder.getOwnerId(), newParentId, folder.getName(), folder.getId());
        folder.moveTo(newParentId, clock.instant());
        return save(folder);
    }

    /**
     * Đưa thư mục cùng mọi thư mục con và tài liệu bên trong vào thùng rác trong một đợt xóa (NCL-05-CN-001 TC-04,
     * QTN-18). Tệp gốc vẫn giữ nguyên để khôi phục được.
     */
    @Transactional
    public Trashed trash(UUID id, ClientInfo client) {
        Folder folder = require(id, client);
        UUID ownerId = folder.getOwnerId();
        Set<UUID> ids = descendantsOf(ownerId, folder.getId());
        UUID batch = UUID.randomUUID();
        Instant now = clock.instant();
        int trashedDocuments = documents.trashByOwnerIdAndFolderIdIn(ownerId, ids, batch, now);
        int trashedFolders = folders.trashByOwnerIdAndIdIn(ownerId, ids, batch, now);
        return new Trashed(batch, trashedFolders, trashedDocuments);
    }

    /** Thư mục và mọi thư mục con cháu đang hoạt động của nó. */
    Set<UUID> descendantsOf(UUID ownerId, UUID folderId) {
        Map<UUID, List<UUID>> children = new HashMap<>();
        for (Folder folder : folders.findAllByOwnerIdAndTrashedAtIsNullOrderByNameAsc(ownerId)) {
            if (folder.getParentId() != null) {
                children.computeIfAbsent(folder.getParentId(), key -> new ArrayList<>()).add(folder.getId());
            }
        }
        Set<UUID> found = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>(List.of(folderId));
        while (!queue.isEmpty()) {
            UUID next = queue.pop();
            if (found.add(next)) {
                queue.addAll(children.getOrDefault(next, List.of()));
            }
        }
        return found;
    }

    private String requireName(String name) {
        String clean = LibraryNames.clean(name);
        if (clean.isEmpty() || clean.length() > Folder.NAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "name");
        }
        return clean;
    }

    private void requireFree(UUID ownerId, UUID parentId, String name, UUID exceptFolderId) {
        Set<String> taken = takenKeys(ownerId, parentId, exceptFolderId);
        if (taken.contains(LibraryNames.key(name))) {
            throw nameTaken(LibraryNames.suggest(name, taken, Folder.NAME_MAX_LENGTH));
        }
    }

    private Set<String> takenKeys(UUID ownerId, UUID parentId, UUID exceptFolderId) {
        List<Folder> siblings = parentId == null
                ? folders.findAllByOwnerIdAndParentIdIsNullAndTrashedAtIsNull(ownerId)
                : folders.findAllByOwnerIdAndParentIdAndTrashedAtIsNull(ownerId, parentId);
        return LibraryNames.keys(siblings.stream().filter(f -> !f.getId().equals(exceptFolderId)).toList(),
                Folder::getName);
    }

    private Folder save(Folder folder) {
        try {
            Folder saved = folders.save(folder);
            entityManager.flush();
            return saved;
        } catch (DataIntegrityViolationException | jakarta.persistence.PersistenceException raced) {
            // Hai lần tạo cùng lúc cùng tên: chỉ mục duy nhất trong cơ sở dữ liệu là chốt chặn cuối.
            throw nameTaken(null);
        }
    }

    static BusinessException nameTaken(String suggestion) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (suggestion != null) {
            details.put("suggestion", suggestion);
        }
        return new BusinessException(ErrorCode.LIBRARY_NAME_TAKEN, "name", details);
    }

    /** Một thư mục trong cây kèm số tài liệu nằm trực tiếp trong nó. */
    public record Node(Folder folder, long documentCount) {
    }

    public record Tree(List<Node> folders, long rootDocumentCount, long totalDocumentCount, long starredCount) {
    }

    public record NameCheck(boolean available, String suggestion) {
    }

    public record Trashed(UUID batchId, int folders, int documents) {
    }

    /** Dùng cho lần chuyển tài liệu: tài liệu có thể nằm ở thư mục gốc hoặc trong thư mục này. */
    List<Document> documentsIn(UUID ownerId, UUID folderId) {
        return folderId == null
                ? documents.findAllByOwnerIdAndFolderIdIsNullAndTrashedAtIsNull(ownerId)
                : documents.findAllByOwnerIdAndFolderIdAndTrashedAtIsNull(ownerId, folderId);
    }
}
