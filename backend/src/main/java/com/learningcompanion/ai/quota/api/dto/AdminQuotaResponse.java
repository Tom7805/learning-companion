package com.learningcompanion.ai.quota.api.dto;

import com.learningcompanion.ai.quota.domain.QuotaTask;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Trang hạn mức của quản trị viên: hạn mức chung, bảng quy đổi và người sửa gần nhất của từng phần. */
public record AdminQuotaResponse(Policy policy, List<Cost> costs) {

    public record Actor(UUID id, String displayName) {
    }

    /** {@code updatedBy} trống khi vẫn là giá trị mặc định lúc cài đặt. */
    public record Policy(int dailyAiUnits, long storageLimitBytes, long maxUploadFileBytes, Instant updatedAt,
                         Actor updatedBy) {
    }

    public record Cost(QuotaTask task, QuotaTask.Weight weight, int units, int defaultUnits, Instant updatedAt,
                       Actor updatedBy) {
    }
}
