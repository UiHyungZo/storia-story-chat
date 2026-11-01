package com.storia.backend.mcp;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * MCP Tool 실행 관측. 호출마다 내용 없는 한 줄 JSON(tool, durationMs, outcome, errorType, results)을 stderr에
 * 남기고(STDIO 서버의 stdout은 MCP 메시지 전용), 프로세스 내 요약을 제공한다. 질의·문서 내용은 기록하지 않는다.
 */
public final class McpToolMetrics {

    public record Summary(long calls, Map<String, Long> byOutcome, Map<String, Long> byErrorType, Double avgMs, Long p95Ms) {
    }

    private final PrintStream log;
    private final List<Long> durations = new ArrayList<>();
    private final Map<String, Long> outcomes = new TreeMap<>();
    private final Map<String, Long> errors = new TreeMap<>();

    public McpToolMetrics(PrintStream log) {
        this.log = log;
    }

    public synchronized void record(String tool, long durationMs, String outcome, String errorType, int results) {
        durations.add(durationMs);
        outcomes.merge(outcome, 1L, Long::sum);
        if (errorType != null) {
            errors.merge(errorType, 1L, Long::sum);
        }
        if (log != null) {
            log.println("{\"event\":\"mcp_tool_call\",\"tool\":\"" + tool + "\",\"durationMs\":" + durationMs
                    + ",\"outcome\":\"" + outcome + "\",\"errorType\":" + (errorType == null ? "null" : "\"" + errorType + "\"")
                    + ",\"results\":" + results + "}");
        }
    }

    public synchronized Summary summary() {
        List<Long> sorted = new ArrayList<>(durations);
        sorted.sort(Long::compare);
        Double avg = sorted.isEmpty() ? null : sorted.stream().mapToLong(Long::longValue).average().orElse(0);
        Long p95 = sorted.isEmpty() ? null : sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
        return new Summary(sorted.size(), new TreeMap<>(outcomes), new TreeMap<>(errors), avg, p95);
    }
}
