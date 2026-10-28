package com.storia.backend.controller;

import com.storia.backend.dto.AdminKnowledgeSearchRequest;
import com.storia.backend.rag.KnowledgeSearchResult;
import com.storia.backend.rag.KnowledgeSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 지식 검색(RAG) 직접 조회/재인덱싱. LLM을 거치지 않으므로 Gemini 비용 없이 검색 품질을 확인할 수 있다.
 * {@code /api/admin/**}이므로 관리자 인증이 적용된다. 재인덱싱은 메모리의 검색 인덱스만 갱신한다.
 */
@RestController
@RequestMapping("/api/admin/ai/knowledge")
@RequiredArgsConstructor
public class AdminKnowledgeController {

    private final KnowledgeSearchService knowledgeSearchService;

    @PostMapping("/search")
    public KnowledgeSearchResult search(@RequestBody AdminKnowledgeSearchRequest request) {
        KnowledgeSearchResult result = knowledgeSearchService.search(request.query(), request.topK());
        return switch (result.status()) {
            case INVALID_INPUT -> throw new IllegalArgumentException(result.message());
            case ERROR -> throw new IllegalStateException("지식 검색 실패");
            default -> result;
        };
    }

    @GetMapping("/status")
    public KnowledgeSearchResult.IndexInfo status() {
        return knowledgeSearchService.status();
    }

    @PostMapping("/reindex")
    public KnowledgeSearchResult.IndexInfo reindex() {
        return knowledgeSearchService.reindex();
    }
}
