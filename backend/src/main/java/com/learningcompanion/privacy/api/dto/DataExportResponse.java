package com.learningcompanion.privacy.api.dto;

import com.learningcompanion.privacy.domain.DataExportRequest;
import com.learningcompanion.privacy.domain.DataExportStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Một lần xuất dữ liệu; kích thước, số tệp và các phần chỉ có khi gói đã sẵn sàng. {@code reauthenticationRequired}
 * chỉ có ở trang tải: cho biết lần tải tới có phải nhập lại mật khẩu (hoặc đăng nhập lại) không.
 */
public record DataExportResponse(UUID id, DataExportStatus status, Instant requestedAt, Instant completedAt,
                                 Instant expiresAt, Long sizeBytes, Integer fileCount, String fileName,
                                 List<Section> sections, Boolean reauthenticationRequired) {

    public static DataExportResponse of(DataExportRequest export) {
        return of(export, null);
    }

    public static DataExportResponse of(DataExportRequest export, Boolean reauthenticationRequired) {
        boolean ready = export.getStatus() == DataExportStatus.READY;
        return new DataExportResponse(export.getId(), export.getStatus(), export.getRequestedAt(),
                export.getCompletedAt(), export.getExpiresAt(),
                ready ? export.getSizeBytes() : null,
                ready ? export.getFileCount() : null,
                ready ? export.getFileName() : null,
                export.getSections().stream()
                        .map(section -> new Section(section.id(), section.title(), section.fileCount()))
                        .toList(),
                ready ? reauthenticationRequired : null);
    }

    public record Section(String id, String title, int fileCount) {
    }
}
