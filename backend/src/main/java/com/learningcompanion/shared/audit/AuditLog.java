package com.learningcompanion.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {

    public enum ActorType { USER, ANONYMOUS, SYSTEM }

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    private ActorType actorType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", nullable = false)
    private AuditObjectType objectType;

    @Column(name = "object_id")
    private UUID objectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> details;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "request_id")
    private String requestId;

    protected AuditLog() {
    }

    AuditLog(Instant occurredAt, ActorType actorType, UUID actorId, AuditAction action,
             AuditObjectType objectType, UUID objectId, Map<String, Object> details,
             String ipAddress, String userAgent, String requestId) {
        this.id = UUID.randomUUID();
        this.occurredAt = occurredAt;
        this.actorType = actorType;
        this.actorId = actorId;
        this.action = action;
        this.objectType = objectType;
        this.objectId = objectId;
        this.details = details;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.requestId = requestId;
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditObjectType getObjectType() {
        return objectType;
    }

    public UUID getObjectId() {
        return objectId;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getRequestId() {
        return requestId;
    }
}
