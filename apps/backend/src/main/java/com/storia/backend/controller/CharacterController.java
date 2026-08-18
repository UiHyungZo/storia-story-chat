package com.storia.backend.controller;

import com.storia.backend.dto.CharacterResponse;
import com.storia.backend.service.CharacterService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/characters")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

    @GetMapping
    public List<CharacterResponse> getCharacters() {
        return characterService.findAll().stream()
                .map(CharacterResponse::from)
                .toList();
    }
}
