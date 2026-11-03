package com.learningcompanion.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Một mã khôi phục dùng một lần của quản trị viên; chỉ lưu băm, mã gốc chỉ hiện một lần lúc thiết lập. */
@Entity
@Table(name = "admin_recovery_codes")
public class RecoveryCode {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected RecoveryCode() {
    }

    public static RecoveryCode issue(UUID accountId, String codeHash, Instant now) {
        RecoveryCode code = new RecoveryCode();
        code.id = UUID.randomUUID();
        code.accountId = accountId;
        code.codeHash = codeHash;
        code.createdAt = now;
        return code;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed(Instant now) {
        usedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
