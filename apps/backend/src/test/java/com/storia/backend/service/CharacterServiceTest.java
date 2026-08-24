package com.storia.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.storia.backend.entity.Character;
import com.storia.backend.repository.CharacterRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CharacterServiceTest {

    @Mock private CharacterRepository characterRepository;

    @InjectMocks private CharacterService characterService;

    @Test
    void findAll_returnsWhatTheRepositoryReturns() {
        Character character = new Character();
        character.setId(1L);
        character.setName("루나");
        when(characterRepository.findAll()).thenReturn(List.of(character));

        List<Character> result = characterService.findAll();

        assertThat(result).containsExactly(character);
    }
}
