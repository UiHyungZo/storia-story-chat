package com.storia.backend.dto;

import com.storia.backend.voice.VoiceTurnSession;
import java.time.Instant;

public record AdminVoiceTurnResponse(
        String turnId,
        String deviceId,
        Long characterId,
        String status,
        Instant createdAt,
        Long assistantMessageId,
        String errorMessage) {

    public static AdminVoiceTurnResponse from(VoiceTurnSession session) {
        return new AdminVoiceTurnResponse(
                session.getTurnId(),
                session.getDeviceId(),
                session.getCharacterId(),
                session.getStatus().name().toLowerCase(),
                session.getCreatedAt(),
                session.getAssistantMessageId(),
                session.getErrorMessage());
    }
}
