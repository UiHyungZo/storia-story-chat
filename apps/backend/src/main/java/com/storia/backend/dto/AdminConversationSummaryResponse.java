package com.storia.backend.dto;

import com.storia.backend.entity.Conversation;
import java.time.Instant;

public record AdminConversationSummaryResponse(
        Long id,
        Long userId,
        String deviceId,
        Long characterId,
        String characterName,
        Instant createdAt) {

    public static AdminConversationSummaryResponse from(Conversation conversation) {
        return new AdminConversationSummaryResponse(
                conversation.getId(),
                conversation.getUser().getId(),
                conversation.getUser().getDeviceId(),
                conversation.getCharacter().getId(),
                conversation.getCharacter().getName(),
                conversation.getCreatedAt());
    }
}
