package com.storia.backend.dto;

import java.util.List;

/**
 * 관리자 AI Agent 응답. 도구 인자/결과는 JSON 문자열로 담는다 — 이 앱의 MVC 직렬화기와
 * com.fasterxml JsonNode를 섞지 않기 위함.
 */
public record AdminAgentResponse(String answer, List<ToolCallTrace> toolCalls, String stopReason) {

    public record ToolCallTrace(String name, String arguments, boolean ok, String result) {
    }
}
