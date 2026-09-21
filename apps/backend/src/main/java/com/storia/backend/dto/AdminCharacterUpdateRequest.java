package com.storia.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminCharacterUpdateRequest(
        @NotBlank String concept,
        @NotBlank String systemPrompt,
        String ttsVoiceId) {
}
