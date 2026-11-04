package com.learningcompanion.devtools;

import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.library.application.DocumentService;
import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.domain.DocumentRepository;
import com.learningcompanion.library.domain.DocumentStatus;
import com.learningcompanion.library.domain.DocumentType;
import com.learningcompanion.library.domain.Folder;
import com.learningcompanion.library.domain.FolderRepository;
import com.learningcompanion.library.domain.Tag;
import com.learningcompanion.library.domain.TagColor;
import com.learningcompanion.library.domain.TagRepository;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.web.ApiPaths;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dựng sẵn một kho tài liệu cho môi trường thử (NCL-05-CN-001): cây thư mục nhiều cấp, nhãn màu và nhiều tài liệu đủ
 * loại, kể cả ba trăm tài liệu của TC-02, khi chức năng tải lên chưa có. Không bao giờ được bật ở môi trường thật.
 */
@RestController
@RequestMapping(ApiPaths.DEV + "/library")
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevLibraryController {

    private static final Map<DocumentType, String[]> FILES = Map.ofEntries(
            Map.entry(DocumentType.PDF, new String[] {"pdf", "application/pdf"}),
            Map.entry(DocumentType.DOCX, new String[] {"docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"}),
            Map.entry(DocumentType.PPTX, new String[] {"pptx",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"}),
            Map.entry(DocumentType.EPUB, new String[] {"epub", "application/epub+zip"}),
            Map.entry(DocumentType.TEXT, new String[] {"txt", "text/plain; charset=utf-8"}),
            Map.entry(DocumentType.IMAGE, new String[] {"png", "image/png"}),
            Map.entry(DocumentType.AUDIO, new String[] {"mp3", "audio/mpeg"}),
            Map.entry(DocumentType.VIDEO, new String[] {"mp4", "video/mp4"}),
            Map.entry(DocumentType.WEB, new String[] {"html", "text/html; charset=utf-8"}));

    private final UserAccountRepository accounts;
    private final DocumentService documentService;
    private final DocumentRepository documents;
    private final FolderRepository folders;
    private final TagRepository tags;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final EntityManager entityManager;

    public DevLibraryController(UserAccountRepository accounts, DocumentService documentService,
                                DocumentRepository documents, FolderRepository folders, TagRepository tags,
                                JdbcTemplate jdbc, Clock clock, EntityManager entityManager) {
        this.entityManager = entityManager;
        this.accounts = accounts;
        this.documentService = documentService;
        this.documents = documents;
        this.folders = folders;
        this.tags = tags;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Thư mục ghi theo đường dẫn "Học kỳ một/Kinh tế vi mô" (tạo luôn thư mục cha còn thiếu); tài liệu trỏ tới thư mục
     * theo cùng đường dẫn và tới nhãn theo tên. {@code addedDaysAgo} lùi ngày thêm để thử lọc theo khoảng thời gian.
     */
    @PostMapping("/seed")
    @Transactional
    public SeedResponse seed(@RequestBody SeedRequest request) {
        UserAccount account = accounts.findByEmail(request.email() == null ? ""
                        : request.email().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        UUID ownerId = account.getId();
        Instant now = clock.instant();
        Map<String, UUID> folderIds = new LinkedHashMap<>();
        for (String path : request.folders() == null ? List.<String>of() : request.folders()) {
            folderId(ownerId, path, folderIds, now);
        }
        Map<String, UUID> tagIds = new LinkedHashMap<>();
        for (TagSeed seed : request.tags() == null ? List.<TagSeed>of() : request.tags()) {
            tagIds.put(seed.name(), tags.save(Tag.create(ownerId, seed.name(),
                    seed.color() == null ? TagColor.SUN : seed.color(), now)).getId());
        }
        List<UUID> created = new ArrayList<>();
        for (DocumentSeed seed : request.documents() == null ? List.<DocumentSeed>of() : request.documents()) {
            DocumentType type = seed.type() == null ? DocumentType.TEXT : seed.type();
            String[] file = FILES.get(type);
            String text = seed.text() == null ? "Nội dung mẫu của " + seed.title() + "." : seed.text();
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            Document document = documentService.create(ownerId, seed.title(), type, text,
                    slug(seed.title()) + "." + file[0], file[1], bytes);
            if (seed.folder() != null && !seed.folder().isBlank()) {
                document.moveTo(folderId(ownerId, seed.folder(), folderIds, now), now);
            }
            if (seed.tags() != null) {
                document.addTags(seed.tags().stream().map(name -> {
                    UUID id = tagIds.get(name);
                    if (id == null) {
                        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "tags");
                    }
                    return id;
                }).toList());
            }
            document.star(Boolean.TRUE.equals(seed.starred()));
            if (seed.status() != null) {
                document.changeStatus(seed.status());
            }
            documents.save(document);
            if (seed.addedDaysAgo() != null && seed.addedDaysAgo() > 0) {
                entityManager.flush();
                Instant added = now.minus(Duration.ofDays(seed.addedDaysAgo()));
                jdbc.update("update documents set created_at = ?, updated_at = ? where id = ?",
                        java.sql.Timestamp.from(added), java.sql.Timestamp.from(added), document.getId());
            }
            created.add(document.getId());
        }
        return new SeedResponse(folderIds, tagIds, created);
    }

    private UUID folderId(UUID ownerId, String path, Map<String, UUID> known, Instant now) {
        UUID parent = null;
        StringBuilder sofar = new StringBuilder();
        for (String segment : path.split("/")) {
            String name = segment.strip();
            if (name.isEmpty()) {
                continue;
            }
            if (!sofar.isEmpty()) {
                sofar.append('/');
            }
            sofar.append(name);
            UUID existing = known.get(sofar.toString());
            if (existing == null) {
                UUID parentId = parent;
                existing = (parentId == null
                        ? folders.findAllByOwnerIdAndParentIdIsNullAndTrashedAtIsNull(ownerId)
                        : folders.findAllByOwnerIdAndParentIdAndTrashedAtIsNull(ownerId, parentId)).stream()
                        .filter(folder -> folder.getName().equalsIgnoreCase(name))
                        .map(Folder::getId)
                        .findFirst()
                        .orElseGet(() -> folders.save(Folder.create(ownerId, parentId, name, now)).getId());
                known.put(sofar.toString(), existing);
            }
            parent = existing;
        }
        return parent;
    }

    private static String slug(String title) {
        String plain = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return plain.isEmpty() ? "tai-lieu" : plain;
    }

    public record SeedRequest(String email, List<String> folders, List<TagSeed> tags, List<DocumentSeed> documents) {
    }

    public record TagSeed(String name, TagColor color) {
    }

    public record DocumentSeed(String title, DocumentType type, String folder, Set<String> tags, Boolean starred,
                               DocumentStatus status, Integer addedDaysAgo, String text) {
    }

    public record SeedResponse(Map<String, UUID> folders, Map<String, UUID> tags, List<UUID> documents) {
    }
}
