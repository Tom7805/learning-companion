package com.learningcompanion.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokeLinkRequest(@NotBlank @Size(max = 128) String token) {
}
