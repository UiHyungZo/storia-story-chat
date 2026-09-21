package com.storia.backend.dto;

import com.storia.backend.entity.Character;
import java.time.Instant;

/** {@link CharacterResponse}와 달리 systemPrompt를 포함한다 — 운영 콘솔 전용이라 노출해도 된다. */
public record AdminCharacterResponse(
        Long id,
        String name,
        String concept,
        String systemPrompt,
        String ttsVoiceId,
        Instant createdAt) {

    public static AdminCharacterResponse from(Character character) {
        return new AdminCharacterResponse(
                character.getId(),
                character.getName(),
                character.getConcept(),
                character.getSystemPrompt(),
                character.getTtsVoiceId(),
                character.getCreatedAt());
    }
}
