package com.storia.backend.rag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 검색 관측 (메모리, 최대 {@value #MAX}건). 질의·문서 내용은 기록하지 않고 결과 상태, 지연, 반환 건수,
 * 임베딩에 보낸 글자 수(외부 임베딩 비용의 대용 지표)만 남긴다.
 */
public final class RagMetrics {

    static final int MAX = 500;

    public record Summary(long searches, Map<String, Long> byStatus, Double avgLatencyMs, Long p95LatencyMs,
                          long queryCharsEmbedded, long indexCharsEmbedded, long rebuilds) {
    }

    private record Entry(KnowledgeSearchResult.Status status, long latencyMs) {
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    private long queryChars;
    private long indexChars;
    private long rebuilds;

    public synchronized void recordSearch(KnowledgeSearchResult.Status status, long latencyMs, int queryLength) {
        entries.addFirst(new Entry(status, latencyMs));
        while (entries.size() > MAX) {
            entries.removeLast();
        }
        if (status != KnowledgeSearchResult.Status.INVALID_INPUT) {
            queryChars += queryLength;
        }
    }

    public synchronized void recordRebuild(long embeddedChars) {
        rebuilds++;
        indexChars += embeddedChars;
    }

    public synchronized Summary summary() {
        Map<KnowledgeSearchResult.Status, Long> counts = new EnumMap<>(KnowledgeSearchResult.Status.class);
        List<Long> latencies = new ArrayList<>();
        for (Entry e : entries) {
            counts.merge(e.status(), 1L, Long::sum);
            latencies.add(e.latencyMs());
        }
        latencies.sort(Long::compare);
        Map<String, Long> byStatus = new java.util.TreeMap<>();
        counts.forEach((k, v) -> byStatus.put(k.name(), v));
        Double avg = latencies.isEmpty() ? null : latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        Long p95 = latencies.isEmpty() ? null : latencies.get((int) Math.ceil(latencies.size() * 0.95) - 1);
        return new Summary(entries.size(), byStatus, avg, p95, queryChars, indexChars, rebuilds);
    }
}
