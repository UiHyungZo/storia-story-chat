package com.storia.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record StartTurnRequest(@NotBlank String roomName, @NotBlank String trackSid) {
}
