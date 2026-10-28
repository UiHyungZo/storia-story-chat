package com.storia.backend.rag;

import java.util.List;

/**
 * RAG 검색 대상 문서의 명시적 허용 목록 (저장소 루트 기준 상대 경로).
 *
 * <p>제외한 것: {@code HANDOFF.md}, {@code TODO.md}(작업 일지 — 실제 테스트 데이터와 개발 환경 정보가 섞여 있음),
 * {@code AGENTS.md}/{@code CLAUDE.md}(에이전트 지침 — 검색 결과에 섞이면 지시문으로 오인될 수 있음),
 * {@code docs/legal/*}(법무 문서). 소스 코드와 설정 파일, 대화 데이터는 대상이 아니다.
 */
public final class KnowledgeSources {

    public static final List<KnowledgeSource> DEFAULT = List.of(
            new KnowledgeSource("readme", "README.md", "Storia README"),
            new KnowledgeSource("prd", "PRD/Storia_PRD_final.md", "Storia PRD"),
            new KnowledgeSource("architecture", "docs/architecture/README.md", "아키텍처"),
            new KnowledgeSource("admin-ai-agent-phase0", "docs/architecture/admin-ai-agent-phase0.md",
                    "관리자 AI Agent Phase 0 설계"),
            new KnowledgeSource("api", "docs/api.md", "API 문서"),
            new KnowledgeSource("decisions", "docs/decisions.md", "설계 결정 기록"),
            new KnowledgeSource("deployment", "docs/deployment.md", "배포"),
            new KnowledgeSource("error-handling", "docs/error-handling.md", "에러 처리"),
            new KnowledgeSource("testing", "docs/testing.md", "테스트"),
            new KnowledgeSource("cicd", "docs/cicd.md", "CI/CD"),
            new KnowledgeSource("erd", "docs/erd/README.md", "ERD"),
            new KnowledgeSource("webrtc-tradeoffs", "docs/blog-webrtc-tradeoffs.md", "WebRTC 트레이드오프"),
            new KnowledgeSource("admin-ai-roadmap", "docs/roadmap/STORIA_ADMIN_AI_AGENT_ROADMAP.md",
                    "관리자 AI Agent 로드맵"),
            new KnowledgeSource("python-sidecar", "apps/python-sidecar/README.md", "Python 사이드카"));

    private KnowledgeSources() {
    }
}
