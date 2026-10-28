package com.storia.backend.rag;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 문서 → 정제 → Chunking → Embedding → Vector Index 파이프라인을 실행하고 현재 인덱스 스냅샷을 보관한다.
 * 스냅샷은 불변이며 재인덱싱 시 통째로 교체된다 (검색 중인 요청은 이전 스냅샷을 그대로 쓴다).
 */
public final class KnowledgeBase {

    /** 변경 확인 최소 간격. 검색마다 파일 시스템을 확인하지 않도록 한다. */
    static final long CHECK_INTERVAL_MS = 5_000;

    public record Snapshot(VectorIndex index, String providerId, boolean semantic, double minScore, int documents,
                           int chunks, int redactions, Instant builtAt, List<String> missing, List<String> rejected,
                           long embeddedChars, String fingerprint) {
    }

    private final DocumentLoader loader;
    private final EmbeddingProvider provider;
    private final MarkdownChunker chunker;
    private final Double minScoreOverride;
    private final long checkIntervalMs;
    private volatile Snapshot snapshot;
    private volatile long lastCheck;

    public KnowledgeBase(DocumentLoader loader, EmbeddingProvider provider, MarkdownChunker chunker,
                         Double minScoreOverride) {
        this(loader, provider, chunker, minScoreOverride, CHECK_INTERVAL_MS);
    }

    KnowledgeBase(DocumentLoader loader, EmbeddingProvider provider, MarkdownChunker chunker,
                  Double minScoreOverride, long checkIntervalMs) {
        this.checkIntervalMs = checkIntervalMs;
        this.loader = loader;
        this.provider = provider;
        this.chunker = chunker;
        this.minScoreOverride = minScoreOverride;
    }

    /** 현재 스냅샷. 아직 없으면 만든다. 문서가 바뀌었으면(확인 간격마다) 다시 만든다. */
    public Snapshot current() {
        Snapshot s = snapshot;
        if (s == null) {
            return rebuild();
        }
        long now = System.currentTimeMillis();
        if (now - lastCheck >= checkIntervalMs) {
            lastCheck = now;
            if (!loader.fingerprint().equals(s.fingerprint())) {
                return rebuild();
            }
        }
        return s;
    }

    /** 문서를 다시 읽어 인덱스를 새로 만든다. 임베딩 실패 시 예외를 던지고 기존 스냅샷은 유지된다. */
    public synchronized Snapshot rebuild() {
        String fingerprint = loader.fingerprint();
        DocumentLoader.Loaded loaded = loader.load();
        List<KnowledgeChunk> chunks = new ArrayList<>();
        int redactions = 0;
        for (KnowledgeDocument doc : loaded.documents()) {
            chunks.addAll(chunker.chunk(doc));
            redactions += doc.redactions();
        }

        List<String> texts = chunks.stream().map(KnowledgeChunk::embeddingText).toList();
        provider.prepare(texts);
        List<float[]> vectors = chunks.isEmpty() ? List.of() : provider.embedDocuments(texts);
        long chars = texts.stream().mapToLong(String::length).sum();

        Snapshot built = new Snapshot(new InMemoryVectorIndex(chunks, vectors), provider.id(), provider.semantic(),
                minScoreOverride != null ? minScoreOverride : provider.defaultMinScore(), loaded.documents().size(),
                chunks.size(), redactions, Instant.now(), loaded.missing(), loaded.rejected(), chars, fingerprint);
        this.snapshot = built;
        this.lastCheck = System.currentTimeMillis();
        return built;
    }

    public EmbeddingProvider provider() {
        return provider;
    }
}
