package com.learningcompanion.library.domain;

import com.learningcompanion.shared.ownership.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Nhãn có màu do người học tạo; một tài liệu gắn được nhiều nhãn (NCL-05-CN-001). */
@Entity
@Table(name = "library_tags")
public class Tag implements OwnedEntity {

    public static final int NAME_MAX_LENGTH = 40;

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TagColor color;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Tag() {
    }

    public static Tag create(UUID ownerId, String name, TagColor color, Instant now) {
        Tag tag = new Tag();
        tag.id = UUID.randomUUID();
        tag.ownerId = ownerId;
        tag.name = name;
        tag.color = color;
        tag.createdAt = now;
        tag.updatedAt = now;
        return tag;
    }

    public void update(String newName, TagColor newColor, Instant now) {
        this.name = newName;
        this.color = newColor;
        this.updatedAt = now;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public TagColor getColor() {
        return color;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
