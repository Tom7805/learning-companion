package com.learningcompanion.profile.application;

import java.util.UUID;

/**
 * Người học đổi múi giờ (QTN-09). Lời nhắc, lịch học và chuỗi ngày học sau này nghe sự kiện này
 * để tính lại các mốc theo múi giờ mới.
 */
public record TimeZoneChangedEvent(UUID ownerId, String previousTimeZone, String newTimeZone) {
}
