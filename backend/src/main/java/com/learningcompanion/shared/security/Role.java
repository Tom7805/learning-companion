package com.learningcompanion.shared.security;

/** Hai vai trò của hệ thống (QTN-01). */
public enum Role {
    LEARNER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
