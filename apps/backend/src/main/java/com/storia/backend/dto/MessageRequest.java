package com.storia.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record MessageRequest(@NotBlank String content) {
}
