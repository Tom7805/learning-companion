package com.learningcompanion.admin.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Quản trị viên đang đăng nhập, để trang quản trị biết mình được mở bởi ai, phiên quản trị hết hạn cứng lúc nào
 * (tám giờ kể từ lúc đăng nhập) và còn bao nhiêu mã khôi phục chưa dùng (NCL-03-CN-002).
 */
public record AdminIdentity(UUID id, String email, String displayName, Instant sessionExpiresAt,
                            long recoveryCodesRemaining) {
}
