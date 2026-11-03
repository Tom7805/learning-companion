package com.learningcompanion.admin.api.dto;

import java.util.UUID;

/** Quản trị viên đang đăng nhập, để trang quản trị biết mình được mở bởi ai. */
public record AdminIdentity(UUID id, String email, String displayName) {
}
