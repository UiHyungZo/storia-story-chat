package com.storia.backend.lore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 캐릭터 세계관 RAG 관측 (메모리, 최대 {@value #MAX}건). 사용자 대화 원문과 질의는 기록하지 않고 캐릭터 키, 상태,
 * 지연만 남긴다.
 */
public final class CharacterLoreMetrics {

    static final int MAX = 500;

    public record IndexState(String state, String provider, Boolean semantic, Integer documents, Integer chunks,
                             String builtAt) {
    }

    public record Summary(boolean enabled, long searches, Map<String, Long> byStatus,
                          Map<String, Map<String, Long>> byCharacter, Double avgLatencyMs, Double p50LatencyMs,
                          Double p95LatencyMs, long isolationViolations, Map<String, IndexState> indexes) {
    }

    private record Entry(String loreKey, LoreStatus status, long latencyMicros) {
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    private long isolationViolations;

    public synchronized void record(String loreKey, LoreStatus status, long latencyMicros) {
        entries.addFirst(new Entry(loreKey == null ? "(none)" : loreKey, status, latencyMicros));
        while (entries.size() > MAX) {
            entries.removeLast();
        }
    }

    public synchronized void recordIsolationViolation() {
        isolationViolations++;
    }

    public synchronized Summary summary(boolean enabled, Map<String, IndexState> indexes) {
        Map<String, Long> byStatus = new TreeMap<>();
        Map<String, Map<String, Long>> byCharacter = new TreeMap<>();
        List<Long> latencies = new ArrayList<>();
        for (Entry e : entries) {
            byStatus.merge(e.status().name(), 1L, Long::sum);
            byCharacter.computeIfAbsent(e.loreKey(), k -> new TreeMap<>()).merge(e.status().name(), 1L, Long::sum);
            latencies.add(e.latencyMicros());
        }
        latencies.sort(Long::compare);
        Double avg = latencies.isEmpty() ? null : ms(latencies.stream().mapToLong(Long::longValue).average().orElse(0));
        return new Summary(enabled, entries.size(), byStatus, byCharacter, avg, percentile(latencies, 0.50),
                percentile(latencies, 0.95), isolationViolations, indexes);
    }

    private static Double percentile(List<Long> sorted, double p) {
        return sorted.isEmpty() ? null : ms(sorted.get((int) Math.ceil(sorted.size() * p) - 1));
    }

    /** 마이크로초 → 밀리초(소수 셋째 자리). 로컬 검색은 1ms 미만이라 정수 ms로는 구분되지 않는다. */
    private static double ms(double micros) {
        return Math.round(micros) / 1000.0;
    }
}
