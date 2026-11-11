package com.learningcompanion.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.learningcompanion.library.application.FolderService;
import com.learningcompanion.library.domain.Folder;
import com.learningcompanion.library.domain.TagColor;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.OwnerContext;
import com.learningcompanion.shared.ownership.OwnershipGuard;
import com.learningcompanion.shared.web.ClientInfo;
import com.learningcompanion.workspace.application.WorkspaceService;
import com.learningcompanion.workspace.domain.Workspace;
import com.learningcompanion.workspace.domain.WorkspaceDocumentRepository;
import com.learningcompanion.workspace.domain.WorkspaceIcon;
import com.learningcompanion.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Quy tắc của không gian học (NCL-06-CN-001), không cần cơ sở dữ liệu. */
class WorkspaceServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T08:00:00Z");
    private static final ClientInfo CLIENT = new ClientInfo("127.0.0.1", "test");

    private final UUID ownerId = UUID.randomUUID();
    private final WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    private final WorkspaceDocumentRepository links = mock(WorkspaceDocumentRepository.class);
    private final FolderService folders = mock(FolderService.class);
    private final OwnershipGuard guard = mock(OwnershipGuard.class);
    private WorkspaceService service;

    @BeforeEach
    void setUp() {
        OwnerContext owner = mock(OwnerContext.class);
        when(owner.requireOwnerId()).thenReturn(ownerId);
        service = new WorkspaceService(workspaces, links, folders, owner, guard, Clock.fixed(NOW, ZoneOffset.UTC));
        when(workspaces.save(any(Workspace.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(links.countActiveByOwnerIdGroupedByWorkspace(ownerId)).thenReturn(List.of());
    }

    @Test
    @DisplayName("Tên được gọn khoảng trắng; biểu tượng và màu để trống thì dùng mặc định")
    void createsWithDefaults() {
        Workspace created = service.create("  Kinh tế   lượng ", null, null, "  Ôn thi  ").workspace();

        assertThat(created.getName()).isEqualTo("Kinh tế lượng");
        assertThat(created.getIcon()).isEqualTo(WorkspaceIcon.BOOK);
        assertThat(created.getColor()).isEqualTo(TagColor.SUN);
        assertThat(created.getDescription()).isEqualTo("Ôn thi");
        assertThat(created.getOwnerId()).isEqualTo(ownerId);
    }

    @Test
    @DisplayName("Tên trống hoặc dài quá một trăm ký tự, mô tả quá năm trăm ký tự đều bị từ chối")
    void rejectsInvalidInput() {
        for (String name : new String[] {null, "   ", "a".repeat(101)}) {
            assertThatThrownBy(() -> service.create(name, null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(error -> {
                        assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                        assertThat(((BusinessException) error).field()).isEqualTo("name");
                    });
        }
        assertThatThrownBy(() -> service.create("Toán", null, null, "a".repeat(501)))
                .satisfies(error -> assertThat(((BusinessException) error).field()).isEqualTo("description"));
        assertThat(service.create("a".repeat(100), null, null, null).workspace().getName()).hasSize(100);
    }

    @Test
    @DisplayName("Không gian học đã vào thùng rác được trả lời như không tồn tại")
    void trashedWorkspaceIsNotFound() {
        Workspace workspace = existing("Xác suất");
        service.trash(workspace.getId(), CLIENT);

        assertThat(workspace.isTrashed()).isTrue();
        assertThat(workspace.getTrashBatchId()).isNotNull();
        assertThatThrownBy(() -> service.open(workspace.getId(), CLIENT))
                .extracting(error -> ((BusinessException) error).code()).isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("Sửa chỉ đổi những gì được gửi lên: bỏ trống biểu tượng, màu, mô tả thì giữ giá trị cũ")
    void updateKeepsOmittedFields() {
        Workspace workspace = existing("Xác suất");
        workspace.edit("Xác suất", WorkspaceIcon.CALCULATOR, TagColor.MINT, "Chương 1", NOW);

        service.update(workspace.getId(), "Xác suất thống kê", null, null, null, CLIENT);

        assertThat(workspace.getName()).isEqualTo("Xác suất thống kê");
        assertThat(workspace.getIcon()).isEqualTo(WorkspaceIcon.CALCULATOR);
        assertThat(workspace.getColor()).isEqualTo(TagColor.MINT);
        assertThat(workspace.getDescription()).isEqualTo("Chương 1");
    }

    @Test
    @DisplayName("Thư mục mặc định: tạo theo tên không gian học, trùng tên thì dùng tên gợi ý, có rồi thì dùng lại")
    void defaultFolderUsesSuggestionAndIsReused() {
        Workspace workspace = existing("Xác suất");
        Folder folder = Folder.create(ownerId, null, "Xác suất (2)", NOW);
        when(folders.checkName(null, "Xác suất", null, CLIENT))
                .thenReturn(new FolderService.NameCheck(false, "Xác suất (2)"));
        when(folders.create(null, "Xác suất (2)", CLIENT)).thenReturn(folder);

        assertThat(service.defaultFolder(workspace.getId(), CLIENT)).isSameAs(folder);
        assertThat(workspace.getDefaultFolderId()).isEqualTo(folder.getId());

        when(folders.findActive(ownerId, folder.getId())).thenReturn(Optional.of(folder));
        assertThat(service.defaultFolder(workspace.getId(), CLIENT)).isSameAs(folder);
        verify(folders).create(null, "Xác suất (2)", CLIENT);

        // Thư mục cũ đã bị xóa: tạo thư mục mới.
        Folder replacement = Folder.create(ownerId, null, "Xác suất", NOW);
        when(folders.findActive(ownerId, folder.getId())).thenReturn(Optional.empty());
        when(folders.checkName(null, "Xác suất", null, CLIENT)).thenReturn(new FolderService.NameCheck(true, null));
        when(folders.create(null, "Xác suất", CLIENT)).thenReturn(replacement);
        assertThat(service.defaultFolder(workspace.getId(), CLIENT)).isSameAs(replacement);
        assertThat(workspace.getDefaultFolderId()).isEqualTo(replacement.getId());
    }

    @Test
    @DisplayName("Danh sách đưa không gian học vừa mở lên đầu")
    void listPutsRecentlyOpenedFirst() {
        Workspace older = Workspace.create(ownerId, "Cũ", WorkspaceIcon.BOOK, TagColor.SUN, "", NOW.minusSeconds(60));
        Workspace newer = Workspace.create(ownerId, "Mới", WorkspaceIcon.BOOK, TagColor.SUN, "", NOW.minusSeconds(30));
        when(workspaces.findAllByOwnerIdAndTrashedAtIsNull(ownerId)).thenReturn(List.of(older, newer));
        when(links.countActiveByOwnerIdGroupedByWorkspace(ownerId))
                .thenReturn(List.<Object[]>of(new Object[] {older.getId(), 3L}));

        assertThat(service.list()).extracting(overview -> overview.workspace().getName()).containsExactly("Mới", "Cũ");

        older.markOpened(NOW);
        assertThat(service.list()).extracting(overview -> overview.workspace().getName()).containsExactly("Cũ", "Mới");
        assertThat(service.list().get(0).documentCount()).isEqualTo(3);
    }

    private Workspace existing(String name) {
        Workspace workspace = Workspace.create(ownerId, name, WorkspaceIcon.BOOK, TagColor.SUN, "", NOW);
        when(guard.require(workspaces, workspace.getId(), AuditObjectType.WORKSPACE, CLIENT)).thenReturn(workspace);
        return workspace;
    }
}
