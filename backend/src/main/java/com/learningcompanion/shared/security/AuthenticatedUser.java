package com.learningcompanion.shared.security;

import java.util.UUID;

/** Người dùng của phiên hiện tại, đặt vào SecurityContext sau khi phiên được xác minh. */
public record AuthenticatedUser(UUID userId, UUID sessionId, Role role) {
}
