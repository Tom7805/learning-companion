package com.learningcompanion.privacy.api.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateConsentRequest(@NotNull Boolean granted) {
}
