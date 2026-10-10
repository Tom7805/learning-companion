package com.learningcompanion.shared.security;

import com.learningcompanion.shared.util.HashUtils;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** Sinh chuỗi bí mật ngẫu nhiên cho phiên và đường dẫn dùng một lần, chỉ lưu băm của chúng. */
@Component
public class SessionTokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    public String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        return HashUtils.sha256Hex(token);
    }
}
