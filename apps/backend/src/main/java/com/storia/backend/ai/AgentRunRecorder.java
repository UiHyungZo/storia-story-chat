package com.storia.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 에이전트 실행 한 번 동안의 관측 값을 모으는 일회용 수집기. 내용(텍스트)은 받지 않는다. */
final class AgentRunRecorder {

    private static final Set<String> KNOWN_TOOLS = Set.of(AdminToolRegistry.GET_CHARACTER_CONFIG,
            AdminToolRegistry.GET_CONVERSATION_LOGS, AdminToolRegistry.PROPOSE_CHARACTER_CONFIG_CHANGE);

    private final String runId = UUID.randomUUID().toString().substring(0, 8);
    private final Instant startedAt = Instant.now();
    private final long startNanos = System.nanoTime();
    private final List<Long> modelLatencies = new ArrayList<>();
    private final List<AgentRunRecord.ToolRecord> tools = new ArrayList<>();
    private Long prompt;
    private Long output;
    private Long total;

    String runId() {
        return runId;
    }

    void modelCall(long latencyMs, JsonNode response) {
        modelLatencies.add(latencyMs);
        JsonNode usage = response == null ? null : response.path("usageMetadata");
        if (usage != null && usage.isObject()) {
            prompt = add(prompt, usage.path("promptTokenCount"));
            output = add(output, usage.path("candidatesTokenCount"));
            total = add(total, usage.path("totalTokenCount"));
        }
    }

    void modelCallFailed(long latencyMs) {
        modelLatencies.add(latencyMs);
    }

    /** 모델이 만든 도구 이름은 신뢰할 수 없으므로 허용 목록에 없으면 이름 대신 "(unknown)"만 기록한다. */
    void tool(String name, boolean ok, String errorCode, long durationMs) {
        String safe = KNOWN_TOOLS.contains(name) ? name : "(unknown)";
        tools.add(new AgentRunRecord.ToolRecord(safe, ok, ok ? null : errorCode, durationMs));
    }

    AgentRunRecord build(String stopReason, String errorType) {
        return new AgentRunRecord(runId, startedAt, (System.nanoTime() - startNanos) / 1_000_000, stopReason, errorType,
                modelLatencies.size(), List.copyOf(modelLatencies), prompt, output, total, List.copyOf(tools));
    }

    private static Long add(Long sum, JsonNode node) {
        if (!node.isNumber()) {
            return sum;
        }
        return (sum == null ? 0L : sum) + node.asLong();
    }
}
