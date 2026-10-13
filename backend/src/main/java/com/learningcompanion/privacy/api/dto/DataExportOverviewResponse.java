package com.learningcompanion.privacy.api.dto;

import com.learningcompanion.privacy.application.DataExportService;
import java.time.Instant;

/** Lần xuất gần nhất (nếu có) và lúc được yêu cầu tiếp; {@code nextAvailableAt} trống khi yêu cầu được ngay. */
public record DataExportOverviewResponse(DataExportResponse latest, boolean canRequest, Instant nextAvailableAt) {

    public static DataExportOverviewResponse of(DataExportService.Overview overview) {
        return new DataExportOverviewResponse(
                overview.latest() == null ? null : DataExportResponse.of(overview.latest()),
                overview.nextAvailableAt() == null, overview.nextAvailableAt());
    }
}
