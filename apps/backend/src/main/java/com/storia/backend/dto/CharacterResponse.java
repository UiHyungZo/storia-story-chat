package com.storia.backend.dto;

import com.storia.backend.entity.Character;

public record CharacterResponse(
        Long id,
        String name,
        String concept,
        String avatarUrl,
        String ttsVoiceId) {

    public static CharacterResponse from(Character character) {
        return new CharacterResponse(
                character.getId(),
                character.getName(),
                character.getConcept(),
                character.getAvatarUrl(),
                character.getTtsVoiceId());
    }
}
