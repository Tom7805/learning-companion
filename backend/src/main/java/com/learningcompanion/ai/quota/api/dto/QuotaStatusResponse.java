package com.learningcompanion.ai.quota.api.dto;

import com.learningcompanion.ai.quota.domain.QuotaTask;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Đồng hồ lượt của người học (NCL-04-CN-003): lượt còn lại trong ngày học, thời điểm hồi lại theo múi giờ của người
 * học, dung lượng lưu trữ và bảng quy đổi để biết phần nào tốn lượt.
 */
public record QuotaStatusResponse(Ai ai, Storage storage, List<TaskCostResponse> costs) {

    public record Ai(int dailyUnits, int usedUnits, int reservedUnits, int remainingUnits, LocalDate studyDay,
                     String timeZone, Instant resetsAt) {
    }

    public record Storage(long limitBytes, long usedBytes, long remainingBytes) {
    }

    public record TaskCostResponse(QuotaTask task, QuotaTask.Weight weight, int units) {
    }
}
