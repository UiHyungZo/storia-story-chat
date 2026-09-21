package com.storia.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.storia.backend.admin.AdminSessionStore;
import com.storia.backend.entity.Character;
import com.storia.backend.entity.Conversation;
import com.storia.backend.entity.Message;
import com.storia.backend.entity.User;
import com.storia.backend.exception.ResourceNotFoundException;
import com.storia.backend.service.AdminConversationService;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminConversationController.class)
class AdminConversationControllerTest {

    private static final Cookie SESSION_COOKIE = new Cookie("ADMIN_SESSION", "token-123");

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AdminConversationService adminConversationService;
    @MockitoBean private AdminSessionStore adminSessionStore;

    @Test
    void search_withoutSessionCookie_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/conversations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void search_withValidSession_returnsPagedConversations() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);
        when(adminConversationService.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(conversation()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/conversations")
                        .cookie(SESSION_COOKIE)
                        .param("characterId", "1")
                        .param("deviceId", "device-1")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].deviceId").value("device-1"))
                .andExpect(jsonPath("$.content[0].characterName").value("루나"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void getMessages_forUnknownConversation_returns404() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);
        when(adminConversationService.getMessages(anyLong(), any()))
                .thenThrow(new ResourceNotFoundException("Conversation not found: 999"));

        mockMvc.perform(get("/api/admin/conversations/999/messages").cookie(SESSION_COOKIE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getMessages_withValidSession_returnsPagedTranscript() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);
        Conversation conversation = conversation();
        Message message = new Message(conversation, Message.Role.USER, "안녕");
        when(adminConversationService.getMessages(anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of(message), PageRequest.of(0, 50), 1));

        mockMvc.perform(get("/api/admin/conversations/1/messages").cookie(SESSION_COOKIE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("안녕"))
                .andExpect(jsonPath("$.content[0].role").value("USER"));
    }

    private Conversation conversation() {
        User user = new User("device-1");
        Character character = new Character();
        character.setId(1L);
        character.setName("루나");
        Conversation conversation = new Conversation(user, character);
        conversation.setId(1L);
        return conversation;
    }
}
