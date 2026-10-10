package com.learningcompanion.auth.api.dto;

public record SessionStatusResponse(boolean authenticated, AccountResponse account) {

    public static SessionStatusResponse anonymous() {
        return new SessionStatusResponse(false, null);
    }
}
