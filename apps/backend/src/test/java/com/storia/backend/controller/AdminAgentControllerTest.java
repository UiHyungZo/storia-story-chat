package com.storia.backend.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.storia.backend.admin.AdminSessionStore;
import com.storia.backend.ai.AdminAgentService;
import com.storia.backend.dto.AdminAgentResponse;
import com.storia.backend.dto.AdminAgentResponse.ToolCallTrace;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminAgentController.class)
class AdminAgentControllerTest {

    private static final Cookie SESSION_COOKIE = new Cookie("ADMIN_SESSION", "token-123");

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AdminAgentService adminAgentService;
    @MockitoBean private AdminSessionStore adminSessionStore;

    @Test
    void query_withoutSession_returns401AndNeverCallsAgent() throws Exception {
        mockMvc.perform(post("/api/admin/ai/agent/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"안녕\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(adminAgentService);
    }

    @Test
    void query_withInvalidSessionToken_returns401() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(false);

        mockMvc.perform(post("/api/admin/ai/agent/query")
                        .cookie(SESSION_COOKIE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"안녕\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminAgentService);
    }

    @Test
    void query_withValidSession_returnsAnswerAndToolTrace() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);
        when(adminAgentService.ask("1번 캐릭터 알려줘")).thenReturn(new AdminAgentResponse("루나입니다.",
                List.of(new ToolCallTrace("getCharacterConfig", "{\"characterId\":1}", true, "{\"name\":\"루나\"}")),
                "completed"));

        mockMvc.perform(post("/api/admin/ai/agent/query")
                        .cookie(SESSION_COOKIE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"1번 캐릭터 알려줘\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("루나입니다."))
                .andExpect(jsonPath("$.stopReason").value("completed"))
                .andExpect(jsonPath("$.toolCalls[0].name").value("getCharacterConfig"))
                .andExpect(jsonPath("$.toolCalls[0].ok").value(true));
        verify(adminAgentService).ask("1번 캐릭터 알려줘");
    }

    @Test
    void query_withBlankOrOversizedQuestion_returns400() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);

        for (String body : List.of("{\"question\":\"\"}", "{\"question\":\"   \"}", "{}",
                "{\"question\":\"" + "가".repeat(1001) + "\"}")) {
            mockMvc.perform(post("/api/admin/ai/agent/query")
                            .cookie(SESSION_COOKIE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(adminAgentService);
    }

    @Test
    void query_whenUpstreamFails_returns502WithGenericMessage() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);
        when(adminAgentService.ask(anyString())).thenThrow(new IllegalStateException("Gemini 호출 실패: 500"));

        mockMvc.perform(post("/api/admin/ai/agent/query")
                        .cookie(SESSION_COOKIE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"안녕\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"));
    }

    @Test
    void query_getMethod_neverReachesAgent() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);

        // 참고: 기존 GlobalExceptionHandler가 405를 catch-all(500)로 처리한다. 여기서는 에이전트가 호출되지 않음만 확인한다.
        mockMvc.perform(get("/api/admin/ai/agent/query").cookie(SESSION_COOKIE))
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getStatus()).isNotEqualTo(200));
        verifyNoInteractions(adminAgentService);
    }
}
