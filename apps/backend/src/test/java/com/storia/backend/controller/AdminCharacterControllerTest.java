package com.storia.backend.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.storia.backend.admin.AdminSessionStore;
import com.storia.backend.entity.Character;
import com.storia.backend.service.CharacterService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminCharacterController.class)
class AdminCharacterControllerTest {

    private static final Cookie SESSION_COOKIE = new Cookie("ADMIN_SESSION", "token-123");

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CharacterService characterService;
    @MockitoBean private AdminSessionStore adminSessionStore;

    @Test
    void list_withoutSessionCookie_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/characters"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void list_withValidSession_returnsCharactersIncludingSystemPrompt() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);
        Character character = character();
        when(characterService.findAll()).thenReturn(List.of(character));

        mockMvc.perform(get("/api/admin/characters").cookie(SESSION_COOKIE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("루나"))
                .andExpect(jsonPath("$[0].systemPrompt").value("따뜻하게 대답해줘"));
    }

    @Test
    void update_withValidSession_updatesAndReturnsCharacter() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);
        Character updated = character();
        updated.setConcept("새 컨셉");
        updated.setSystemPrompt("새 프롬프트");
        when(characterService.update(1L, "새 컨셉", "새 프롬프트", "ko-KR-Standard-A")).thenReturn(updated);

        mockMvc.perform(put("/api/admin/characters/1")
                        .cookie(SESSION_COOKIE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"concept\":\"새 컨셉\",\"systemPrompt\":\"새 프롬프트\",\"ttsVoiceId\":\"ko-KR-Standard-A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concept").value("새 컨셉"))
                .andExpect(jsonPath("$.systemPrompt").value("새 프롬프트"));
    }

    @Test
    void update_withBlankSystemPrompt_returns400ValidationError() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);

        mockMvc.perform(put("/api/admin/characters/1")
                        .cookie(SESSION_COOKIE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"concept\":\"새 컨셉\",\"systemPrompt\":\"\",\"ttsVoiceId\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private Character character() {
        Character character = new Character();
        character.setId(1L);
        character.setName("루나");
        character.setConcept("따뜻한 상담사");
        character.setSystemPrompt("따뜻하게 대답해줘");
        character.setTtsVoiceId("ko-KR-Standard-A");
        return character;
    }
}
