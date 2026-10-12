package com.learningcompanion.auth.api.dto;

import jakarta.validation.constraints.NotNull;

public record SetPasswordRequest(@NotNull String newPassword) {
}
