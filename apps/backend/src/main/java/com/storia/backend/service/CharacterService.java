package com.storia.backend.service;

import com.storia.backend.entity.Character;
import com.storia.backend.exception.ResourceNotFoundException;
import com.storia.backend.repository.CharacterRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CharacterService {

    private final CharacterRepository characterRepository;

    public List<Character> findAll() {
        return characterRepository.findAll();
    }

    public Character findById(Long id) {
        return characterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Character not found: " + id));
    }

    @Transactional
    public Character update(Long id, String concept, String systemPrompt, String ttsVoiceId) {
        Character character = findById(id);
        character.setConcept(concept);
        character.setSystemPrompt(systemPrompt);
        character.setTtsVoiceId(ttsVoiceId);
        return character;
    }
}
