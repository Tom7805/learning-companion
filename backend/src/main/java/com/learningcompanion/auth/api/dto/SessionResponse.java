package com.learningcompanion.auth.api.dto;

import com.learningcompanion.auth.domain.UserSession;
import java.time.Instant;
import java.util.UUID;

/** Một thiết bị đang đăng nhập trong danh sách thiết bị của người học. */
public record SessionResponse(
        UUID id,
        String browser,
        String operatingSystem,
        String deviceType,
        String location,
        boolean localNetwork,
        Instant signedInAt,
        Instant lastActiveAt,
        Instant expiresAt,
        boolean rememberDevice,
        boolean current) {

    public static SessionResponse of(UserSession session, UUID currentSessionId) {
        var device = session.device();
        return new SessionResponse(session.getId(), device.browser(), device.operatingSystem(),
                device.deviceType() == null ? null : device.deviceType().name(), device.location(),
                device.localNetwork(), session.getCreatedAt(), session.getLastActiveAt(), session.getExpiresAt(),
                session.isRememberDevice(), session.getId().equals(currentSessionId));
    }
}
