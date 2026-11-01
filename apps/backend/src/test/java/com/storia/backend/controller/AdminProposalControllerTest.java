package com.storia.backend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.storia.backend.admin.AdminSessionStore;
import com.storia.backend.ai.ProposalApprovalService;
import com.storia.backend.dto.AdminProposalResponse;
import com.storia.backend.dto.AdminProposalResponse.Fields;
import com.storia.backend.exception.ConflictException;
import com.storia.backend.exception.ResourceNotFoundException;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminProposalController.class)
class AdminProposalControllerTest {

    private static final Cookie SESSION_COOKIE = new Cookie("ADMIN_SESSION", "token-123");

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ProposalApprovalService approvalService;
    @MockitoBean private AdminSessionStore adminSessionStore;

    private AdminProposalResponse response(String status) {
        return new AdminProposalResponse("p-1", 1L, "루나", status, "이유", Instant.now(), null, false,
                List.of("concept"), new Fields("원래", "프롬프트", "voice"), new Fields("새", null, null));
    }

    @Test
    void everyEndpoint_withoutSession_returns401AndNeverTouchesService() throws Exception {
        mockMvc.perform(get("/api/admin/ai/proposals")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/ai/proposals/p-1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/ai/proposals/p-1/approve")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/ai/proposals/p-1/reject")).andExpect(status().isUnauthorized());
        verifyNoInteractions(approvalService);
    }

    @Test
    void list_andGet_returnDiffData() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);
        when(approvalService.list()).thenReturn(List.of(response("PENDING")));
        when(approvalService.get("p-1")).thenReturn(response("PENDING"));

        mockMvc.perform(get("/api/admin/ai/proposals").cookie(SESSION_COOKIE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("p-1"));
        mockMvc.perform(get("/api/admin/ai/proposals/p-1").cookie(SESSION_COOKIE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.concept").value("원래"))
                .andExpect(jsonPath("$.proposed.concept").value("새"))
                .andExpect(jsonPath("$.changedFields[0]").value("concept"));
    }

    @Test
    void approve_withoutExplicitConfirm_returns400AndDoesNotApprove() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);

        for (String body : new String[] {"{}", "{\"confirm\":false}", "{\"confirm\":null}"}) {
            mockMvc.perform(post("/api/admin/ai/proposals/p-1/approve").cookie(SESSION_COOKIE)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
        mockMvc.perform(post("/api/admin/ai/proposals/p-1/approve").cookie(SESSION_COOKIE))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(approvalService);
    }

    @Test
    void approve_withConfirm_approves() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);
        when(approvalService.approve("p-1")).thenReturn(response("APPROVED"));

        mockMvc.perform(post("/api/admin/ai/proposals/p-1/approve").cookie(SESSION_COOKIE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        verify(approvalService).approve("p-1");
    }

    @Test
    void approve_staleOrAlreadyHandled_returns409() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);
        when(approvalService.approve("p-1")).thenThrow(new ConflictException("제안 이후 캐릭터 설정이 변경되어 적용할 수 없습니다."));

        mockMvc.perform(post("/api/admin/ai/proposals/p-1/approve").cookie(SESSION_COOKIE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void reject_and_unknownProposal() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);
        when(approvalService.reject("p-1")).thenReturn(response("REJECTED"));
        when(approvalService.reject("nope")).thenThrow(new ResourceNotFoundException("Proposal not found: nope"));

        mockMvc.perform(post("/api/admin/ai/proposals/p-1/reject").cookie(SESSION_COOKIE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(post("/api/admin/ai/proposals/nope/reject").cookie(SESSION_COOKIE))
                .andExpect(status().isNotFound());
    }
}
