package com.learningcompanion.privacy.application;

import com.learningcompanion.notification.application.NotificationTargetResolver;
import com.learningcompanion.notification.domain.NotificationTargetType;
import com.learningcompanion.privacy.domain.DataExportRequestRepository;
import com.learningcompanion.privacy.domain.DataExportStatus;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thông báo "gói dữ liệu đã sẵn sàng" mở trang tải gói. Gói đã hết hạn hoặc được thay bằng gói mới thì tệp đã bị
 * xóa: thông báo mở trang "nội dung không còn" (NCL-02-CN-002, TC-03).
 */
@Component
public class DataExportNotificationTarget implements NotificationTargetResolver {

    private final DataExportRequestRepository exports;
    private final Clock clock;

    public DataExportNotificationTarget(DataExportRequestRepository exports, Clock clock) {
        this.exports = exports;
        this.clock = clock;
    }

    @Override
    public NotificationTargetType type() {
        return NotificationTargetType.DATA_EXPORT;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> pathOf(UUID ownerId, UUID targetId) {
        if (targetId == null) {
            return Optional.empty();
        }
        return exports.findByIdAndOwnerId(targetId, ownerId)
                .filter(export -> export.getStatus() == DataExportStatus.READY && !export.isPastExpiry(clock.instant()))
                .map(export -> "/exports/" + export.getId());
    }
}
