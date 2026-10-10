package com.learningcompanion.shared.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Băm mật khẩu bằng PBKDF2-HMAC-SHA256 theo mức khuyến nghị của Spring Security,
 * không giới hạn 72 byte như BCrypt nên hợp với cụm mật khẩu tiếng Việt dài.
 */
@Component
public class PasswordHasher {

    private final PasswordEncoder encoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String hash) {
        return encoder.matches(rawPassword, hash);
    }
}
