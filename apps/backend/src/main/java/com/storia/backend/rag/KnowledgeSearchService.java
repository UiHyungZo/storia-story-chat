package com.storia.backend.rag;

import com.storia.backend.rag.KnowledgeSearchResult.Hit;
import com.storia.backend.rag.KnowledgeSearchResult.IndexInfo;
import com.storia.backend.rag.KnowledgeSearchResult.Status;
import java.util.ArrayList;
import java.util.List;

/**
 * 질의 검증 → 질의 임베딩 → 벡터 검색 → 점수 기준 필터 → 출처 포함 결과. Gemini Agent, 관리자 검색 API,
 * MCP 서버가 모두 이 서비스 하나를 재사용한다 (검색 로직의 중복 구현 없음). 읽기 전용이다 — 문서와 DB를 쓰지 않는다.
 */
public final class KnowledgeSearchService {

    public static final int MAX_QUERY_CHARS = 300;
    public static final int DEFAULT_TOP_K = 3;
    public static final int MAX_TOP_K = 5;
    public static final int MAX_SNIPPET_CHARS = 800;

    private final KnowledgeBase base;
    private final RagMetrics metrics;

    public KnowledgeSearchService(KnowledgeBase base, RagMetrics metrics) {
        this.base = base;
        this.metrics = metrics;
    }

    public KnowledgeSearchResult search(String query, Integer topK) {
        long start = System.nanoTime();
        KnowledgeSearchResult result = doSearch(query, topK, start);
        metrics.recordSearch(result.status(), result.tookMs(), query == null ? 0 : query.length());
        return result;
    }

    /** 문서 변경 후 인덱스를 즉시 다시 만든다. */
    public KnowledgeSearchResult.IndexInfo reindex() {
        KnowledgeBase.Snapshot snapshot = base.rebuild();
        metrics.recordRebuild(snapshot.embeddedChars());
        return info(snapshot);
    }

    public KnowledgeSearchResult.IndexInfo status() {
        return info(base.current());
    }

    private KnowledgeSearchResult doSearch(String query, Integer topK, long start) {
        String problem = validate(query, topK);
        if (problem != null) {
            return new KnowledgeSearchResult(Status.INVALID_INPUT, problem, List.of(), null, tookMs(start));
        }
        int k = topK == null ? DEFAULT_TOP_K : topK;

        KnowledgeBase.Snapshot snapshot;
        float[] queryVector;
        try {
            snapshot = base.current();
            if (snapshot.chunks() == 0) {
                return new KnowledgeSearchResult(Status.INDEX_EMPTY, "검색할 문서가 인덱싱되어 있지 않습니다.", List.of(),
                        info(snapshot), tookMs(start));
            }
            queryVector = base.provider().embedQuery(query.strip());
        } catch (RuntimeException e) {
            // 원인 메시지에는 외부 응답/경로가 섞일 수 있으므로 일반화된 메시지만 반환한다.
            return new KnowledgeSearchResult(Status.ERROR, "검색을 수행하지 못했습니다. (" + e.getClass().getSimpleName() + ")",
                    List.of(), null, tookMs(start));
        }

        List<Hit> hits = new ArrayList<>();
        for (VectorIndex.Scored scored : snapshot.index().search(queryVector, k)) {
            if (scored.score() < snapshot.minScore()) {
                break;
            }
            KnowledgeChunk c = scored.chunk();
            hits.add(new Hit(hits.size() + 1, c.chunkId(), c.docId(), c.source(), c.title(), c.heading(),
                    truncate(c.text()), Math.round(scored.score() * 10_000.0) / 10_000.0));
        }
        if (hits.isEmpty()) {
            return new KnowledgeSearchResult(Status.NO_EVIDENCE, "질문과 관련된 근거 문서를 찾지 못했습니다.", List.of(),
                    info(snapshot), tookMs(start));
        }
        return new KnowledgeSearchResult(Status.OK, null, hits, info(snapshot), tookMs(start));
    }

    static String validate(String query, Integer topK) {
        if (query == null || query.isBlank()) {
            return "query는 비어 있을 수 없습니다.";
        }
        if (query.length() > MAX_QUERY_CHARS) {
            return "query는 " + MAX_QUERY_CHARS + "자 이하여야 합니다.";
        }
        if (query.chars().anyMatch(ch -> Character.isISOControl(ch) && ch != '\n' && ch != '\t')) {
            return "query에 제어 문자를 포함할 수 없습니다.";
        }
        if (topK != null && (topK < 1 || topK > MAX_TOP_K)) {
            return "topK는 1~" + MAX_TOP_K + " 사이여야 합니다.";
        }
        return null;
    }

    private static String truncate(String text) {
        return text.length() <= MAX_SNIPPET_CHARS ? text : text.substring(0, MAX_SNIPPET_CHARS);
    }

    private static IndexInfo info(KnowledgeBase.Snapshot s) {
        return new IndexInfo(s.providerId(), s.semantic(), s.documents(), s.chunks(), s.builtAt().toString(), s.missing());
    }

    private static long tookMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
