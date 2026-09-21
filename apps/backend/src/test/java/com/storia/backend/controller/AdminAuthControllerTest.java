package com.storia.backend.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.storia.backend.admin.AdminSessionStore;
import com.storia.backend.config.AdminProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminAuthController.class)
class AdminAuthControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AdminProperties adminProperties;
    @MockitoBean private AdminSessionStore adminSessionStore;

    @Test
    void login_withCorrectPassword_setsSessionCookieAndReturnsToken() throws Exception {
        when(adminProperties.isConfigured()).thenReturn(true);
        when(adminProperties.password()).thenReturn("devpassword");
        when(adminSessionStore.issue()).thenReturn("token-123");

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"devpassword\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-123"))
                .andExpect(cookie().value("ADMIN_SESSION", "token-123"))
                .andExpect(cookie().httpOnly("ADMIN_SESSION", true));
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        when(adminProperties.isConfigured()).thenReturn(true);
        when(adminProperties.password()).thenReturn("devpassword");

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void login_whenAdminPasswordNotConfigured_returns401() throws Exception {
        when(adminProperties.isConfigured()).thenReturn(false);

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"anything\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void session_withValidCookie_returns200() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);

        mockMvc.perform(get("/api/admin/auth/session").cookie(new Cookie("ADMIN_SESSION", "token-123")))
                .andExpect(status().isOk());
    }

    @Test
    void session_withoutCookie_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void logout_invalidatesSessionAndClearsCookie() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);

        mockMvc.perform(post("/api/admin/auth/logout").cookie(new Cookie("ADMIN_SESSION", "token-123")))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("ADMIN_SESSION", 0));

        verify(adminSessionStore).invalidate(eq("token-123"));
    }
}
