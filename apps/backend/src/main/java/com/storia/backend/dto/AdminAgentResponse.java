package com.storia.backend.dto;

import java.util.List;

/**
 * 관리자 AI Agent 응답. 도구 인자/결과는 JSON 문자열로 담는다 — 이 앱의 MVC 직렬화기와
 * com.fasterxml JsonNode를 섞지 않기 위함.
 *
 * <p>지식 검색(RAG)을 사용한 경우 {@code knowledgeSources}에 근거 문서 경로가, {@code knowledgeStatus}에 마지막
 * 검색 상태가 담긴다. 마지막 검색이 근거를 찾지 못했으면(NO_EVIDENCE 등) {@code evidenceNotice}가 채워진다 —
 * 모델 답변이 문서로 확인되지 않았음을 서버가 고정 문구로 알리는 장치이다.
 */
public record AdminAgentResponse(String answer, List<ToolCallTrace> toolCalls, String stopReason,
                                 List<String> knowledgeSources, String knowledgeStatus, String evidenceNotice) {

    public AdminAgentResponse(String answer, List<ToolCallTrace> toolCalls, String stopReason) {
        this(answer, toolCalls, stopReason, List.of(), null, null);
    }

    public record ToolCallTrace(String name, String arguments, boolean ok, String result) {
    }
}
