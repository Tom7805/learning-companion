package com.learningcompanion.shared.security;

import java.util.Optional;

/** Tra phiên còn hiệu lực từ chuỗi bí mật trong cookie; mô-đun auth cung cấp phần hiện thực. */
public interface SessionResolver {

    Optional<AuthenticatedUser> resolve(String rawToken);
}
