package com.storia.backend.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.storia.backend.admin.AdminSessionStore;
import com.storia.backend.ai.AgentRunRecord;
import com.storia.backend.ai.AgentTelemetryStore;
import com.storia.backend.dto.AdminObservabilityResponse.Summary;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminObservabilityController.class)
class AdminObservabilityControllerTest {

    private static final Cookie SESSION_COOKIE = new Cookie("ADMIN_SESSION", "token-123");

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AgentTelemetryStore telemetryStore;
    @MockitoBean private AdminSessionStore adminSessionStore;

    @Test
    void get_withoutSession_returns401AndReadsNothing() throws Exception {
        mockMvc.perform(get("/api/admin/ai/observability")).andExpect(status().isUnauthorized());
        verifyNoInteractions(telemetryStore);
    }

    @Test
    void get_withValidSession_returnsSummaryAndRecentRuns() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);
        when(telemetryStore.summary()).thenReturn(new Summary(1, 200, 24, Instant.now(), Map.of("completed", 1L),
                Map.of(), 120.0, 120L, 50.0, null, List.of()));
        when(telemetryStore.recent(50)).thenReturn(List.of(new AgentRunRecord("abc12345", Instant.now(), 120,
                "completed", null, 1, List.of(50L), null, null, null, List.of())));

        mockMvc.perform(get("/api/admin/ai/observability").cookie(SESSION_COOKIE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.runs").value(1))
                .andExpect(jsonPath("$.summary.byStopReason.completed").value(1))
                .andExpect(jsonPath("$.recentRuns[0].runId").value("abc12345"))
                .andExpect(jsonPath("$.recentRuns[0].totalTokens").doesNotExist());
    }
}
