package com.learningcompanion.library.domain;

import com.learningcompanion.shared.ownership.OwnedEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

/**
 * Tài liệu học tập của một người học; tệp gốc nằm mã hóa trong kho tệp, ở đây chỉ giữ khóa của nó. Trong kho tài liệu
 * (NCL-05-CN-001) tài liệu nằm trong một thư mục (rỗng là thư mục gốc), gắn được nhiều nhãn và gắn sao. Xóa chỉ đưa
 * vào thùng rác (QTN-18).
 */
@Entity
@Table(name = "documents")
public class Document implements OwnedEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType type;

    @Column(name = "content_text", nullable = false)
    private String contentText;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false, length = 127)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 100)
    private String storageKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "folder_id")
    private UUID folderId;

    @Column(nullable = false)
    private boolean starred;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.READY;

    @Column(name = "last_opened_at")
    private Instant lastOpenedAt;

    @Column(name = "trashed_at")
    private Instant trashedAt;

    @Column(name = "trash_batch_id")
    private UUID trashBatchId;

    // Dấu vân tay nội dung (SHA-256 của tệp gốc) để báo trùng trong toàn kho (QTN-17); tài liệu cũ có thể để trống.
    @Column(name = "content_sha256", updatable = false, length = 64)
    private String contentSha256;

    // Nhãn luôn đi kèm thẻ tài liệu; đọc theo lô để danh sách hàng trăm tài liệu chỉ tốn thêm một truy vấn.
    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 256)
    @CollectionTable(name = "library_document_tags", joinColumns = @JoinColumn(name = "document_id"))
    @Column(name = "tag_id", nullable = false)
    private Set<UUID> tagIds = new LinkedHashSet<>();

    @Version
    private long version;

    protected Document() {
    }

    public static Document create(UUID ownerId, String title, DocumentType type, String contentText,
                                  String originalFilename, String mimeType, long sizeBytes, String storageKey,
                                  Instant now) {
        return create(ownerId, title, type, contentText, originalFilename, mimeType, sizeBytes, storageKey, null, now);
    }

    public static Document create(UUID ownerId, String title, DocumentType type, String contentText,
                                  String originalFilename, String mimeType, long sizeBytes, String storageKey,
                                  String contentSha256, Instant now) {
        Document document = new Document();
        document.id = UUID.randomUUID();
        document.ownerId = ownerId;
        document.title = title;
        document.type = type;
        document.contentText = contentText;
        document.originalFilename = originalFilename;
        document.mimeType = mimeType;
        document.sizeBytes = sizeBytes;
        document.storageKey = storageKey;
        document.contentSha256 = contentSha256;
        document.createdAt = now;
        document.updatedAt = now;
        return document;
    }

    public void rename(String newTitle, Instant now) {
        this.title = newTitle;
        this.updatedAt = now;
    }

    public void moveTo(UUID newFolderId, Instant now) {
        this.folderId = newFolderId;
        this.updatedAt = now;
    }

    public void star(boolean value) {
        this.starred = value;
    }

    /** Lần mở gần nhất, cho lối tắt "Gần đây" và cách sắp xếp theo lần mở; không đổi ngày cập nhật. */
    public void markOpened(Instant now) {
        this.lastOpenedAt = now;
    }

    public void replaceTags(Collection<UUID> newTagIds) {
        tagIds.clear();
        tagIds.addAll(newTagIds);
    }

    public void addTags(Collection<UUID> more) {
        tagIds.addAll(more);
    }

    public void removeTags(Collection<UUID> fewer) {
        tagIds.removeAll(fewer);
    }

    public void trash(UUID batch, Instant now) {
        this.trashedAt = now;
        this.trashBatchId = batch;
    }

    public void changeStatus(DocumentStatus newStatus) {
        this.status = newStatus;
    }

    public boolean isTrashed() {
        return trashedAt != null;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getOwnerId() {
        return ownerId;
    }

    public String getTitle() {
        return title;
    }

    public DocumentType getType() {
        return type;
    }

    public String getContentText() {
        return contentText;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getFolderId() {
        return folderId;
    }

    public boolean isStarred() {
        return starred;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public Instant getLastOpenedAt() {
        return lastOpenedAt;
    }

    public Instant getTrashedAt() {
        return trashedAt;
    }

    public UUID getTrashBatchId() {
        return trashBatchId;
    }

    public String getContentSha256() {
        return contentSha256;
    }

    public Set<UUID> getTagIds() {
        return Set.copyOf(tagIds);
    }
}
