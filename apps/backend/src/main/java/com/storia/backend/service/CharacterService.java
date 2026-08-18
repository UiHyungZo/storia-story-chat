package com.storia.backend.service;

import com.storia.backend.entity.Character;
import com.storia.backend.repository.CharacterRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CharacterService {

    private final CharacterRepository characterRepository;

    public List<Character> findAll() {
        return characterRepository.findAll();
    }
}
