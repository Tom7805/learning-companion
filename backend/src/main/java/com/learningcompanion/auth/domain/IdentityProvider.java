package com.learningcompanion.auth.domain;

import java.util.Arrays;
import java.util.Optional;

/** Nhà cung cấp định danh bên ngoài được hỗ trợ (NCL-01-CN-007). */
public enum IdentityProvider {
    GOOGLE("google", "Google");

    private final String id;
    private final String displayName;

    IdentityProvider(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<IdentityProvider> fromId(String id) {
        return Arrays.stream(values()).filter(provider -> provider.id.equalsIgnoreCase(id)).findFirst();
    }
}
