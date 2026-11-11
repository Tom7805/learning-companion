package com.learningcompanion.ai.labeling;

import com.learningcompanion.shared.ownership.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Người học đã đọc thông báo đang trò chuyện với hệ thống trí tuệ nhân tạo, phiên bản nào (NCL-04-CN-005 TC-01). */
@Entity
@Table(name = "ai_disclosure_acknowledgements")
public class AiDisclosureAcknowledgement implements OwnedEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private int version;

    @Column(name = "acknowledged_at", nullable = false)
    private Instant acknowledgedAt;

    protected AiDisclosureAcknowledgement() {
    }

    static AiDisclosureAcknowledgement of(UUID ownerId, int version, Instant now) {
        AiDisclosureAcknowledgement acknowledgement = new AiDisclosureAcknowledgement();
        acknowledgement.id = UUID.randomUUID();
        acknowledgement.ownerId = ownerId;
        acknowledgement.version = version;
        acknowledgement.acknowledgedAt = now;
        return acknowledgement;
    }

    void acknowledge(int newVersion, Instant now) {
        version = newVersion;
        acknowledgedAt = now;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getOwnerId() {
        return ownerId;
    }

    public int getVersion() {
        return version;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }
}
