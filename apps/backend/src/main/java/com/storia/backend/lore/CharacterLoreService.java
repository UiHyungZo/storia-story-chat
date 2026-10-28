package com.storia.backend.lore;

import com.storia.backend.rag.ClasspathDocumentLoader;
import com.storia.backend.rag.EmbeddingProvider;
import com.storia.backend.rag.KnowledgeBase;
import com.storia.backend.rag.KnowledgeSearchResult;
import com.storia.backend.rag.KnowledgeSearchService;
import com.storia.backend.rag.MarkdownChunker;
import com.storia.backend.rag.RagMetrics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.function.LongFunction;

/**
 * 캐릭터별 세계관 검색: {@code searchCharacterLore(characterId, query, topK)}.
 *
 * <p>격리: ① 캐릭터 → 세계관 키 해석 후 <b>그 키의 인덱스만</b> 검색한다(캐릭터마다 별도 {@link KnowledgeBase}와
 * 별도 임베딩 제공자 인스턴스 — 다른 캐릭터의 청크는 검색 대상에 들어오지도 않는다). ② 반환 직전에 모든 hit의
 * docId가 해석된 키와 같은지 다시 확인하고, 다르면 버리고 위반으로 기록한다(방어적 이중 확인).
 *
 * <p>안전한 실패: 기능 꺼짐·매핑 없음·타임아웃·오류는 모두 예외 없이 상태로 반환되며, 호출자는 세계관 없이 기존
 * System Prompt만으로 대화를 이어간다. 검색은 고정 크기 스레드 풀에서 시간 예산 안에 수행된다. 읽기 전용이다.
 */
public final class CharacterLoreService {

    private record LoreIndex(KnowledgeBase base, KnowledgeSearchService search) {
    }

    private final boolean enabled;
    private final CharacterLoreCatalog catalog;
    private final LongFunction<Optional<String>> characterNames;
    private final Supplier<EmbeddingProvider> providerFactory;
    private final Double minScore;
    private final long timeoutMs;
    private final ExecutorService executor;
    private final CharacterLoreMetrics metrics;
    private final ClassLoader classLoader;
    private final RagMetrics internalRagMetrics = new RagMetrics();
    private final Map<String, LoreIndex> indexes = new ConcurrentHashMap<>();

    public CharacterLoreService(boolean enabled, CharacterLoreCatalog catalog,
                                LongFunction<Optional<String>> characterNames,
                                Supplier<EmbeddingProvider> providerFactory, Double minScore, long timeoutMs,
                                ExecutorService executor, CharacterLoreMetrics metrics, ClassLoader classLoader) {
        this.enabled = enabled;
        this.catalog = catalog;
        this.characterNames = characterNames;
        this.providerFactory = providerFactory;
        this.minScore = minScore;
        this.timeoutMs = timeoutMs;
        this.executor = executor;
        this.metrics = metrics;
        this.classLoader = classLoader;
    }

    public boolean enabled() {
        return enabled;
    }

    public CharacterLoreResult searchCharacterLore(Long characterId, String query, Integer topK) {
        if (!enabled) {
            return CharacterLoreResult.of(LoreStatus.DISABLED, null, 0);
        }
        long start = System.nanoTime();
        CharacterLoreResult result;
        try {
            result = searchWithBudget(characterId, query, topK, start);
        } catch (RuntimeException e) {
            result = CharacterLoreResult.of(LoreStatus.ERROR, null, micros(start));
        }
        metrics.record(result.loreKey(), result.status(), result.tookMicros());
        return result;
    }

    private CharacterLoreResult searchWithBudget(Long characterId, String query, Integer topK, long start) {
        if (characterId == null) {
            return CharacterLoreResult.of(LoreStatus.INVALID_INPUT, null, micros(start));
        }
        Future<CharacterLoreResult> future;
        try {
            future = executor.submit(() -> doSearch(characterId, query, topK, start));
        } catch (RejectedExecutionException e) {
            return CharacterLoreResult.of(LoreStatus.ERROR, null, micros(start));
        }
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return CharacterLoreResult.of(LoreStatus.TIMEOUT, null, micros(start));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CharacterLoreResult.of(LoreStatus.ERROR, null, micros(start));
        } catch (ExecutionException e) {
            return CharacterLoreResult.of(LoreStatus.ERROR, null, micros(start));
        }
    }

    private CharacterLoreResult doSearch(Long characterId, String query, Integer topK, long start) {
        Optional<String> name = characterNames.apply(characterId);
        Optional<CharacterLoreCatalog.Entry> entry = name.flatMap(catalog::resolve);
        if (entry.isEmpty()) {
            return CharacterLoreResult.of(LoreStatus.NO_LORE, null, micros(start));
        }
        String key = entry.get().loreKey();
        KnowledgeSearchResult result = index(entry.get()).search().search(query, topK);

        switch (result.status()) {
            case INVALID_INPUT:
                return CharacterLoreResult.of(LoreStatus.INVALID_INPUT, key, micros(start));
            case INDEX_EMPTY:
                return CharacterLoreResult.of(LoreStatus.NO_LORE, key, micros(start));
            case NO_EVIDENCE:
                return CharacterLoreResult.of(LoreStatus.NO_EVIDENCE, key, micros(start));
            case ERROR:
                return CharacterLoreResult.of(LoreStatus.ERROR, key, micros(start));
            default:
                break;
        }

        List<KnowledgeSearchResult.Hit> safe = new ArrayList<>();
        for (KnowledgeSearchResult.Hit hit : result.results()) {
            if (key.equals(hit.docId())) {
                safe.add(hit);
            } else {
                metrics.recordIsolationViolation();
            }
        }
        if (safe.isEmpty()) {
            return CharacterLoreResult.of(LoreStatus.NO_EVIDENCE, key, micros(start));
        }
        return new CharacterLoreResult(LoreStatus.OK, key, List.copyOf(safe), micros(start));
    }

    private LoreIndex index(CharacterLoreCatalog.Entry entry) {
        return indexes.computeIfAbsent(entry.loreKey(), k -> {
            KnowledgeBase base = new KnowledgeBase(
                    new ClasspathDocumentLoader(classLoader, List.of(entry.source())),
                    providerFactory.get(), new MarkdownChunker(800), minScore);
            return new LoreIndex(base, new KnowledgeSearchService(base, internalRagMetrics));
        });
    }

    /** 기동 직후 백그라운드에서 인덱스를 미리 만들어 첫 대화 요청이 인덱싱을 기다리지 않게 한다. 실패는 무시한다. */
    public void warmUpAsync() {
        if (!enabled) {
            return;
        }
        for (CharacterLoreCatalog.Entry entry : catalog.entries()) {
            try {
                executor.execute(() -> {
                    try {
                        index(entry).base().current();
                    } catch (RuntimeException ignored) {
                        // 다음 검색에서 다시 시도한다.
                    }
                });
            } catch (RejectedExecutionException ignored) {
                // 검색이 우선이다.
            }
        }
    }

    public CharacterLoreMetrics.Summary summary() {
        Map<String, CharacterLoreMetrics.IndexState> states = new LinkedHashMap<>();
        for (CharacterLoreCatalog.Entry entry : catalog.entries()) {
            LoreIndex index = indexes.get(entry.loreKey());
            KnowledgeBase.Snapshot snapshot = index == null ? null : index.base().peek();
            states.put(entry.loreKey(), snapshot == null
                    ? new CharacterLoreMetrics.IndexState("NOT_BUILT", null, null, null, null, null)
                    : new CharacterLoreMetrics.IndexState(snapshot.chunks() == 0 ? "EMPTY" : "READY",
                            snapshot.providerId(), snapshot.semantic(), snapshot.documents(), snapshot.chunks(),
                            snapshot.builtAt().toString()));
        }
        return metrics.summary(enabled, states);
    }

    private static long micros(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000;
    }
}
