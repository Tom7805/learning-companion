package com.learningcompanion.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.learningcompanion.library.application.FolderService;
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
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Quy tắc của cây thư mục (NCL-05-CN-001), không cần cơ sở dữ liệu. */
class FolderServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");
    private static final ClientInfo CLIENT = new ClientInfo("127.0.0.1", "test");

    private final UUID ownerId = UUID.randomUUID();
    private final FolderRepository folders = mock(FolderRepository.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final OwnershipGuard guard = mock(OwnershipGuard.class);
    private FolderService service;

    private Folder year;
    private Folder semester;
    private Folder calculus;
    private Folder archive;

    @BeforeEach
    void setUp() {
        OwnerContext owner = mock(OwnerContext.class);
        when(owner.requireOwnerId()).thenReturn(ownerId);
        service = new FolderService(folders, documents, owner, guard, Clock.fixed(NOW, ZoneOffset.UTC),
                mock(EntityManager.class));
        year = Folder.create(ownerId, null, "Năm nhất", NOW);
        semester = Folder.create(ownerId, year.getId(), "Học kỳ một", NOW);
        calculus = Folder.create(ownerId, semester.getId(), "Giải tích", NOW);
        archive = Folder.create(ownerId, null, "Lưu trữ", NOW);
        List<Folder> all = List.of(year, semester, calculus, archive);
        when(folders.findAllByOwnerIdAndTrashedAtIsNullOrderByNameAsc(ownerId)).thenReturn(all);
        for (Folder folder : all) {
            when(guard.require(folders, folder.getId(), AuditObjectType.LIBRARY_FOLDER, CLIENT)).thenReturn(folder);
        }
        when(folders.findAllByOwnerIdAndParentIdIsNullAndTrashedAtIsNull(ownerId)).thenReturn(List.of(year, archive));
        when(folders.findAllByOwnerIdAndParentIdAndTrashedAtIsNull(ownerId, year.getId())).thenReturn(List.of(semester));
        when(folders.save(any(Folder.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Không chuyển thư mục vào chính nó hay vào thư mục cháu của nó")
    void rejectsCycles() {
        assertThatThrownBy(() -> service.move(year.getId(), calculus.getId(), CLIENT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code()).isEqualTo(ErrorCode.LIBRARY_FOLDER_CYCLE);
        assertThatThrownBy(() -> service.move(year.getId(), year.getId(), CLIENT))
                .extracting(e -> ((BusinessException) e).code()).isEqualTo(ErrorCode.LIBRARY_FOLDER_CYCLE);
        assertThat(year.getParentId()).isNull();
    }

    @Test
    @DisplayName("Tên trùng với thư mục anh em, không phân biệt hoa thường, bị báo kèm tên gợi ý")
    void duplicateNameSuggestsAlternative() {
        assertThatThrownBy(() -> service.create(year.getId(), "  học KỲ một ", CLIENT))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.LIBRARY_NAME_TAKEN);
                    assertThat(ex.details()).containsEntry("suggestion", "học KỲ một (2)");
                });
        verify(folders, never()).save(any());
        assertThat(service.checkName(year.getId(), "Học kỳ hai", null, CLIENT).available()).isTrue();
        // Đổi tên chính nó (chỉ đổi hoa thường) không bị coi là trùng.
        assertThat(service.rename(semester.getId(), "Học Kỳ Một", CLIENT).getName()).isEqualTo("Học Kỳ Một");
    }

    @Test
    @DisplayName("Xóa thư mục đưa thư mục, mọi thư mục con cháu và tài liệu bên trong vào cùng một đợt thùng rác")
    void trashesWholeSubtreeInOneBatch() {
        when(documents.trashByOwnerIdAndFolderIdIn(eq(ownerId), anyCollection(), any(), eq(NOW))).thenReturn(10);
        when(folders.trashByOwnerIdAndIdIn(eq(ownerId), anyCollection(), any(), eq(NOW))).thenReturn(3);

        FolderService.Trashed trashed = service.trash(year.getId(), CLIENT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<UUID> batch = ArgumentCaptor.forClass(UUID.class);
        verify(folders).trashByOwnerIdAndIdIn(eq(ownerId), ids.capture(), batch.capture(), eq(NOW));
        assertThat(Set.copyOf(ids.getValue())).containsExactlyInAnyOrder(year.getId(), semester.getId(),
                calculus.getId());
        verify(documents).trashByOwnerIdAndFolderIdIn(ownerId, ids.getValue(), batch.getValue(), NOW);
        assertThat(trashed).isEqualTo(new FolderService.Trashed(batch.getValue(), 3, 10));
    }
}
