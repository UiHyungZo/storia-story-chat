package com.storia.backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.storia.backend.admin.AdminSessionStore;
import com.storia.backend.voice.VoiceTurnRegistry;
import com.storia.backend.voice.VoiceTurnSession;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminSessionController.class)
class AdminSessionControllerTest {

    private static final Cookie SESSION_COOKIE = new Cookie("ADMIN_SESSION", "token-123");

    @Autowired private MockMvc mockMvc;

    @MockitoBean private VoiceTurnRegistry voiceTurnRegistry;
    @MockitoBean private AdminSessionStore adminSessionStore;

    @Test
    void list_withoutSessionCookie_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/sessions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withValidSession_returnsTurnsNewestFirst() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);
        VoiceTurnSession older = new VoiceTurnSession("turn-old", "device-1", 1L);
        VoiceTurnSession newer = new VoiceTurnSession("turn-new", "device-2", 2L);
        newer.markProcessing();
        when(voiceTurnRegistry.all()).thenReturn(List.of(older, newer));

        mockMvc.perform(get("/api/admin/sessions").cookie(SESSION_COOKIE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].turnId").value("turn-new"))
                .andExpect(jsonPath("$[0].status").value("processing"))
                .andExpect(jsonPath("$[1].turnId").value("turn-old"))
                .andExpect(jsonPath("$[1].status").value("recording"));
    }
}
