package com.storia.backend.ai;

import com.storia.backend.dto.AdminObservabilityResponse.Summary;
import com.storia.backend.dto.AdminObservabilityResponse.ToolStats;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * 에이전트 실행 기록을 메모리에만 보관한다 (외부 관측 서비스로 전송하지 않음).
 * 보존: 최대 {@value #MAX_RECORDS}건, 최대 24시간 — 접근 시점에 정리하며 서버 재시작 시 사라진다.
 * 조회는 관리자 인증이 필요한 {@code /api/admin/ai/observability}로만 가능하다.
 */
@Component
public class AgentTelemetryStore {

    static final int MAX_RECORDS = 200;
    static final Duration RETENTION = Duration.ofHours(24);

    private final Deque<AgentRunRecord> records = new ArrayDeque<>();
    private final Clock clock;

    public AgentTelemetryStore() {
        this(Clock.systemUTC());
    }

    AgentTelemetryStore(Clock clock) {
        this.clock = clock;
    }

    public synchronized void add(AgentRunRecord record) {
        purge();
        records.addFirst(record);
        while (records.size() > MAX_RECORDS) {
            records.removeLast();
        }
    }

    /** 최신순. */
    public synchronized List<AgentRunRecord> recent(int limit) {
        purge();
        return records.stream().limit(limit).toList();
    }

    public synchronized Summary summary() {
        purge();
        List<AgentRunRecord> runs = new ArrayList<>(records);
        Map<String, Long> byStop = new TreeMap<>();
        Map<String, Long> byError = new TreeMap<>();
        Map<String, long[]> toolAgg = new TreeMap<>(); // [calls, errors, totalMs]
        List<Long> durations = new ArrayList<>();
        long modelMsTotal = 0;
        long modelCalls = 0;
        Long tokens = null;

        for (AgentRunRecord r : runs) {
            byStop.merge(r.stopReason(), 1L, Long::sum);
            if (r.errorType() != null) {
                byError.merge(r.errorType(), 1L, Long::sum);
            }
            durations.add(r.durationMs());
            for (long ms : r.modelLatenciesMs()) {
                modelMsTotal += ms;
                modelCalls++;
            }
            if (r.totalTokens() != null) {
                tokens = (tokens == null ? 0L : tokens) + r.totalTokens();
            }
            for (AgentRunRecord.ToolRecord t : r.tools()) {
                long[] agg = toolAgg.computeIfAbsent(t.name(), k -> new long[3]);
                agg[0]++;
                agg[1] += t.ok() ? 0 : 1;
                agg[2] += t.durationMs();
            }
        }

        durations.sort(Comparator.naturalOrder());
        Double avgDuration = runs.isEmpty() ? null : durations.stream().mapToLong(Long::longValue).average().orElse(0);
        Long p95 = durations.isEmpty() ? null : durations.get((int) Math.ceil(durations.size() * 0.95) - 1);
        Double avgModel = modelCalls == 0 ? null : (double) modelMsTotal / modelCalls;
        List<ToolStats> toolStats = toolAgg.entrySet().stream()
                .map(e -> new ToolStats(e.getKey(), e.getValue()[0], e.getValue()[1],
                        (double) e.getValue()[2] / e.getValue()[0]))
                .toList();
        Instant oldest = runs.isEmpty() ? null : runs.get(runs.size() - 1).startedAt();
        return new Summary(runs.size(), MAX_RECORDS, RETENTION.toHours(), oldest, byStop, byError, avgDuration, p95,
                avgModel, tokens, toolStats);
    }

    private void purge() {
        Instant cutoff = clock.instant().minus(RETENTION);
        while (!records.isEmpty() && records.peekLast().startedAt().isBefore(cutoff)) {
            records.removeLast();
        }
    }
}
