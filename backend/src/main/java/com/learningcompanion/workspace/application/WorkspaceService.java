package com.learningcompanion.workspace.application;

import com.learningcompanion.library.application.FolderService;
import com.learningcompanion.library.application.LibraryNames;
import com.learningcompanion.library.domain.Folder;
import com.learningcompanion.library.domain.TagColor;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.OwnerContext;
import com.learningcompanion.shared.ownership.OwnershipGuard;
import com.learningcompanion.shared.web.ClientInfo;
import com.learningcompanion.workspace.domain.Workspace;
import com.learningcompanion.workspace.domain.WorkspaceDocumentRepository;
import com.learningcompanion.workspace.domain.WorkspaceIcon;
import com.learningcompanion.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Không gian học của người học (NCL-06-CN-001): tạo, sửa, mở và đưa vào thùng rác, không giới hạn số lượng (QTN-14).
 * Không gian học của người khác, hay đã vào thùng rác, được trả lời như không tồn tại.
 */
@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaces;
    private final WorkspaceDocumentRepository links;
    private final FolderService folders;
    private final OwnerContext owner;
    private final OwnershipGuard guard;
    private final Clock clock;

    public WorkspaceService(WorkspaceRepository workspaces, WorkspaceDocumentRepository links, FolderService folders,
                            OwnerContext owner, OwnershipGuard guard, Clock clock) {
        this.workspaces = workspaces;
        this.links = links;
        this.folders = folders;
        this.owner = owner;
        this.guard = guard;
        this.clock = clock;
    }

    /** Mọi không gian học đang hoạt động kèm số tài liệu đang gắn; vừa mở hay vừa tạo đứng trước. */
    @Transactional(readOnly = true)
    public List<Overview> list() {
        UUID ownerId = owner.requireOwnerId();
        Map<UUID, Long> counts = documentCounts(ownerId);
        return workspaces.findAllByOwnerIdAndTrashedAtIsNull(ownerId).stream()
                .sorted(Comparator.comparing(Workspace::lastUsedAt).reversed()
                        .thenComparing(Workspace::getName, String.CASE_INSENSITIVE_ORDER))
                .map(workspace -> new Overview(workspace, counts.getOrDefault(workspace.getId(), 0L)))
                .toList();
    }

    @Transactional
    public Overview create(String name, WorkspaceIcon icon, TagColor color, String description) {
        Workspace workspace = Workspace.create(owner.requireOwnerId(), requireName(name),
                icon == null ? WorkspaceIcon.BOOK : icon, color == null ? TagColor.SUN : color,
                cleanDescription(description), clock.instant());
        return new Overview(workspaces.save(workspace), 0);
    }

    /** Mở trang không gian học: ghi lại lần mở để danh sách trên thanh bên đưa nó lên đầu. */
    @Transactional
    public Overview open(UUID id, ClientInfo client) {
        Workspace workspace = require(id, client);
        workspace.markOpened(clock.instant());
        return overview(workspace);
    }

    @Transactional
    public Overview update(UUID id, String name, WorkspaceIcon icon, TagColor color, String description,
                           ClientInfo client) {
        Workspace workspace = require(id, client);
        workspace.edit(requireName(name), icon == null ? workspace.getIcon() : icon,
                color == null ? workspace.getColor() : color,
                description == null ? workspace.getDescription() : cleanDescription(description), clock.instant());
        return overview(workspace);
    }

    /**
     * Đưa không gian học vào thùng rác (QTN-18). Tài liệu vẫn nguyên trong kho; liên kết được giữ để khôi phục không
     * gian học là có lại đúng nhóm tài liệu.
     */
    @Transactional
    public Workspace trash(UUID id, ClientInfo client) {
        Workspace workspace = require(id, client);
        workspace.trash(UUID.randomUUID(), clock.instant());
        return workspace;
    }

    /**
     * Thư mục trong kho nhận tệp tải lên ngay tại không gian học. Tạo khi lần đầu cần tới, mang tên không gian học (trùng
     * tên thư mục khác ở gốc kho thì thêm số thứ tự); thư mục cũ đã bị xóa thì tạo thư mục mới.
     */
    @Transactional
    public Folder defaultFolder(UUID id, ClientInfo client) {
        Workspace workspace = require(id, client);
        UUID current = workspace.getDefaultFolderId();
        if (current != null) {
            var existing = folders.findActive(workspace.getOwnerId(), current);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        FolderService.NameCheck check = folders.checkName(null, workspace.getName(), null, client);
        Folder created = folders.create(null, check.available() ? workspace.getName() : check.suggestion(), client);
        workspace.useDefaultFolder(created.getId());
        return created;
    }

    /** Không gian học đang hoạt động của người đang đăng nhập; của người khác thì 404 và lần từ chối được ghi nhật ký. */
    @Transactional(readOnly = true)
    public Workspace require(UUID id, ClientInfo client) {
        Workspace workspace = guard.require(workspaces, id, AuditObjectType.WORKSPACE, client);
        if (workspace.isTrashed()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return workspace;
    }

    Overview overview(Workspace workspace) {
        return new Overview(workspace, documentCounts(workspace.getOwnerId()).getOrDefault(workspace.getId(), 0L));
    }

    private Map<UUID, Long> documentCounts(UUID ownerId) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : links.countActiveByOwnerIdGroupedByWorkspace(ownerId)) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private static String requireName(String name) {
        String clean = LibraryNames.clean(name);
        if (clean.isEmpty() || clean.length() > Workspace.NAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "name");
        }
        return clean;
    }

    private static String cleanDescription(String description) {
        String clean = description == null ? "" : description.strip();
        if (clean.length() > Workspace.DESCRIPTION_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "description");
        }
        return clean;
    }

    /** Một không gian học kèm số tài liệu còn trong kho đang gắn với nó. */
    public record Overview(Workspace workspace, long documentCount) {
    }
}
