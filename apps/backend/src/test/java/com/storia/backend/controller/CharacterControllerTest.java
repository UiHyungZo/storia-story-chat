package com.storia.backend.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;

import com.storia.backend.admin.AdminSessionStore;
import com.storia.backend.entity.Character;
import com.storia.backend.service.CharacterService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CharacterController.class)
class CharacterControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CharacterService characterService;

    // AdminWebConfig(WebMvcConfigurer)가 모든 @WebMvcTest 슬라이스에 자동으로 로드되며 이 빈을
    // 요구한다 — /api/characters는 /api/admin/**이 아니라 인터셉터가 실제로 개입하진 않는다.
    @MockitoBean private AdminSessionStore adminSessionStore;

    @Test
    void getCharacters_returnsMappedCharacterList() throws Exception {
        Character character = new Character();
        character.setId(1L);
        character.setName("루나");
        character.setConcept("따뜻한 상담사");
        when(characterService.findAll()).thenReturn(List.of(character));

        mockMvc.perform(get("/api/characters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("루나"))
                .andExpect(jsonPath("$[0].concept").value("따뜻한 상담사"));
    }

    @Test
    void getCharacters_returnsEmptyListWhenNoneExist() throws Exception {
        when(characterService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/characters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
