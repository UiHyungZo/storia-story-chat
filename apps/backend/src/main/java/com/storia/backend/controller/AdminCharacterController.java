package com.storia.backend.controller;

import com.storia.backend.dto.AdminCharacterResponse;
import com.storia.backend.dto.AdminCharacterUpdateRequest;
import com.storia.backend.service.CharacterService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/characters")
@RequiredArgsConstructor
public class AdminCharacterController {

    private final CharacterService characterService;

    @GetMapping
    public List<AdminCharacterResponse> list() {
        return characterService.findAll().stream().map(AdminCharacterResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AdminCharacterResponse get(@PathVariable Long id) {
        return AdminCharacterResponse.from(characterService.findById(id));
    }

    @PutMapping("/{id}")
    public AdminCharacterResponse update(@PathVariable Long id, @Valid @RequestBody AdminCharacterUpdateRequest request) {
        return AdminCharacterResponse.from(
                characterService.update(id, request.concept(), request.systemPrompt(), request.ttsVoiceId()));
    }
}
