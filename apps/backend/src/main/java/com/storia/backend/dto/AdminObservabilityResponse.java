package com.storia.backend.dto;

import com.storia.backend.ai.AgentRunRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 관리자 AI Agent 실행 관측 요약. 내용(질문/답변/도구 인자·결과)은 포함하지 않는다. */
public record AdminObservabilityResponse(Summary summary, List<AgentRunRecord> recentRuns,
                                         com.storia.backend.rag.RagMetrics.Summary knowledge,
                                         com.storia.backend.lore.CharacterLoreMetrics.Summary characterLore) {

    public record Summary(
            int runs,
            int maxRecords,
            long retentionHours,
            Instant oldestRunAt,
            Map<String, Long> byStopReason,
            Map<String, Long> byErrorType,
            Double avgDurationMs,
            Long p95DurationMs,
            Double avgModelLatencyMs,
            /** 보고된 값만 합산. 하나도 보고되지 않았으면 null. */
            Long totalTokens,
            List<ToolStats> tools) {
    }

    public record ToolStats(String name, long calls, long errors, double avgDurationMs) {
    }
}
