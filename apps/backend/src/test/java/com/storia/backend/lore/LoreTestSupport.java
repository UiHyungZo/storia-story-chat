package com.storia.backend.lore;

import com.storia.backend.rag.EmbeddingProvider;
import com.storia.backend.rag.LocalHashingEmbeddingProvider;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** 세계관 RAG 테스트 조립 헬퍼. 캐릭터 ID는 DB 없이 고정 매핑(1=아리아, 2=렌, 3=노아)을 쓴다. */
public final class LoreTestSupport {

    public static final Map<Long, String> NAMES = Map.of(1L, "아리아 (Aria)", 2L, "렌 (Ren)", 3L, "노아 (Noah)");

    private LoreTestSupport() {
    }

    public static ExecutorService executor() {
        return Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "lore-test");
            t.setDaemon(true);
            return t;
        });
    }

    public static CharacterLoreService service(boolean enabled, Supplier<EmbeddingProvider> provider, Double minScore,
                                               long timeoutMs, CharacterLoreMetrics metrics) {
        return new CharacterLoreService(enabled, CharacterLoreCatalog.DEFAULT, id -> Optional.ofNullable(NAMES.get(id)),
                provider, minScore, timeoutMs, executor(), metrics, LoreTestSupport.class.getClassLoader());
    }

    public static CharacterLoreService local(Double minScore) {
        return service(true, LocalHashingEmbeddingProvider::new, minScore, 5_000, new CharacterLoreMetrics());
    }
}
