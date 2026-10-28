package com.storia.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.storia.backend.admin.AdminSessionStore;
import com.storia.backend.rag.KnowledgeSearchResult;
import com.storia.backend.rag.KnowledgeSearchService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminKnowledgeController.class)
class AdminKnowledgeControllerTest {

    private static final Cookie SESSION_COOKIE = new Cookie("ADMIN_SESSION", "token-123");
    private static final KnowledgeSearchResult.IndexInfo INFO =
            new KnowledgeSearchResult.IndexInfo("local-hashing-v1", false, 14, 279, "2026-10-28T00:00:00Z", List.of());

    @Autowired private MockMvc mockMvc;

    @MockitoBean private KnowledgeSearchService knowledgeSearchService;
    @MockitoBean private AdminSessionStore adminSessionStore;

    @Test
    void everyEndpoint_withoutSession_returns401AndNeverSearchesOrReindexes() throws Exception {
        mockMvc.perform(post("/api/admin/ai/knowledge/search").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"q\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/ai/knowledge/status")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/ai/knowledge/reindex")).andExpect(status().isUnauthorized());
        verifyNoInteractions(knowledgeSearchService);
    }

    @Test
    void search_returnsHitsWithSourceAndScore() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);
        var hit = new KnowledgeSearchResult.Hit(1, "api#1", "api", "docs/api.md", "API", "API > 개요", "발췌", 0.42);
        when(knowledgeSearchService.search("API", 2)).thenReturn(
                new KnowledgeSearchResult(KnowledgeSearchResult.Status.OK, null, List.of(hit), INFO, 5));

        mockMvc.perform(post("/api/admin/ai/knowledge/search").cookie(SESSION_COOKIE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"API\",\"topK\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.results[0].source").value("docs/api.md"))
                .andExpect(jsonPath("$.results[0].score").value(0.42))
                .andExpect(jsonPath("$.index.provider").value("local-hashing-v1"));
    }

    @Test
    void search_noEvidenceIs200WithEmptyResults_invalidInputIs400_failureIs502() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);
        when(knowledgeSearchService.search("날씨", null)).thenReturn(
                new KnowledgeSearchResult(KnowledgeSearchResult.Status.NO_EVIDENCE, "근거 없음", List.of(), INFO, 1));
        when(knowledgeSearchService.search("", null)).thenReturn(
                new KnowledgeSearchResult(KnowledgeSearchResult.Status.INVALID_INPUT, "query는 비어 있을 수 없습니다.", List.of(), null, 0));
        when(knowledgeSearchService.search("fail", null)).thenReturn(
                new KnowledgeSearchResult(KnowledgeSearchResult.Status.ERROR, "내부 원인", List.of(), null, 0));

        mockMvc.perform(post("/api/admin/ai/knowledge/search").cookie(SESSION_COOKIE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"날씨\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_EVIDENCE"))
                .andExpect(jsonPath("$.results").isEmpty());
        mockMvc.perform(post("/api/admin/ai/knowledge/search").cookie(SESSION_COOKIE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mockMvc.perform(post("/api/admin/ai/knowledge/search").cookie(SESSION_COOKIE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"fail\"}"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"));
    }

    @Test
    void statusAndReindex_requireValidSessionAndReturnIndexInfo() throws Exception {
        when(adminSessionStore.isValid("token-123")).thenReturn(true);
        when(knowledgeSearchService.status()).thenReturn(INFO);
        when(knowledgeSearchService.reindex()).thenReturn(INFO);

        mockMvc.perform(get("/api/admin/ai/knowledge/status").cookie(SESSION_COOKIE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.chunks").value(279));
        mockMvc.perform(post("/api/admin/ai/knowledge/reindex").cookie(SESSION_COOKIE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.documents").value(14));
    }
}
