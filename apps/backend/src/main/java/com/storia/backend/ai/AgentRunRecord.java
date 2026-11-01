package com.storia.backend.ai;

import java.time.Instant;
import java.util.List;

/**
 * 에이전트 실행 1회의 관측 기록. 진단에 필요한 메타데이터(지연, 단계 수, 도구 이름/성공 여부/오류 코드,
 * 토큰 사용량, 오류 유형)만 담는다. 질문, 답변, 도구 인자·결과, 대화·캐릭터 내용, 키/토큰은 담지 않는다.
 * 토큰 수는 Gemini가 {@code usageMetadata}를 주지 않으면 null이다(0이 아님).
 */
public record AgentRunRecord(
        String runId,
        Instant startedAt,
        long durationMs,
        String stopReason,
        String errorType,
        int modelCalls,
        List<Long> modelLatenciesMs,
        Long promptTokens,
        Long outputTokens,
        Long totalTokens,
        List<ToolRecord> tools) {

    public record ToolRecord(String name, boolean ok, String errorCode, long durationMs) {
    }
}
