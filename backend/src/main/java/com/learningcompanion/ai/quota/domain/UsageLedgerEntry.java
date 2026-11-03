package com.learningcompanion.ai.quota.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Sổ lượt của một người học trong một ngày học (NCL-04-CN-003): số lượt đã trừ và số lượt đang giữ chỗ. Ngày học tính
 * theo múi giờ của người học (QTN-09), nên sang ngày mới là một dòng mới và lượt tự hồi đủ, không cần tác vụ đặt lại.
 * Hai con số chỉ đổi qua câu lệnh cập nhật có điều kiện trong {@link UsageLedgerRepository}.
 */
@Entity
@Table(name = "quota_daily_usage")
public class UsageLedgerEntry {

    @EmbeddedId
    private Key key;

    @Column(name = "used_units", nullable = false)
    private int usedUnits;

    @Column(name = "reserved_units", nullable = false)
    private int reservedUnits;

    protected UsageLedgerEntry() {
    }

    public Key getKey() {
        return key;
    }

    public int getUsedUnits() {
        return usedUnits;
    }

    public int getReservedUnits() {
        return reservedUnits;
    }

    @Embeddable
    public record Key(@Column(name = "owner_id") UUID ownerId, @Column(name = "study_day") LocalDate studyDay)
            implements Serializable {
    }
}
