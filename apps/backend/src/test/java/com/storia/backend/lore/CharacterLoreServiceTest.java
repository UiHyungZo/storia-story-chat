package com.storia.backend.lore;

import static org.assertj.core.api.Assertions.assertThat;

import com.storia.backend.rag.EmbeddingProvider;
import com.storia.backend.rag.KnowledgeSearchResult;
import com.storia.backend.rag.LocalHashingEmbeddingProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CharacterLoreServiceTest {

    /** 호출 횟수를 세는 로컬 임베딩. */
    static class CountingProvider extends LocalHashingEmbeddingProvider {
        final AtomicInteger documentCalls = new AtomicInteger();
        final AtomicInteger queryCalls = new AtomicInteger();
        volatile long queryDelayMs;
        volatile boolean failQuery;

        @Override
        public List<float[]> embedDocuments(List<String> texts) {
            documentCalls.incrementAndGet();
            return super.embedDocuments(texts);
        }

        @Override
        public float[] embedQuery(String text) {
            queryCalls.incrementAndGet();
            if (queryDelayMs > 0) {
                try {
                    Thread.sleep(queryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failQuery) {
                throw new IllegalStateException("boom secret-host");
            }
            return super.embedQuery(text);
        }
    }

    private static final String ARIA_Q = "함장 이름이 뭐야?";

    @Test
    void searchesOnlyTheOwnCharactersLore() {
        CharacterLoreService service = LoreTestSupport.local(0.0);
        for (long id : new long[] {1, 2, 3}) {
            String key = id == 1 ? "aria" : id == 2 ? "ren" : "noah";
            for (String query : List.of(ARIA_Q, "서점 이름", "탐정 사무소", "우주선 항로", "책 추천", "사건 기록")) {
                CharacterLoreResult r = service.searchCharacterLore(id, query, 5);
                assertThat(r.hits()).allSatisfy(h -> assertThat(h.docId()).isEqualTo(key));
                assertThat(r.hits()).allSatisfy(h -> assertThat(h.source()).isEqualTo("characters/" + key + "/lore.md"));
            }
        }
        assertThat(service.summary().isolationViolations()).isZero();
    }

    @Test
    void anotherCharactersFactsAreNeverReturned() {
        CharacterLoreService service = LoreTestSupport.local(0.0);
        // 아리아에게 렌의 고유 사실을 물어도 렌 문서 청크는 나오지 않는다 (minScore 0으로 최대한 많이 반환시킴).
        CharacterLoreResult r = service.searchCharacterLore(1L, "달무리 서점 보리차 마 선생님", 5);
        assertThat(r.hits()).allSatisfy(h -> assertThat(h.text()).doesNotContain("달무리").doesNotContain("보리차"));
    }

    @Test
    void mappingFollowsCharacterNameNotId() {
        // 같은 이름이 다른 ID로 재생성되어도 같은 세계관에 연결되고, 다른 이름은 연결되지 않는다.
        CharacterLoreService service = new CharacterLoreService(true, CharacterLoreCatalog.DEFAULT,
                id -> id == 777L ? Optional.of("렌 (Ren)") : id == 1L ? Optional.of("아리아 (Aria)")
                        : id == 5L ? Optional.of("이름이 바뀐 캐릭터") : Optional.empty(),
                LocalHashingEmbeddingProvider::new, 0.0, 5_000, LoreTestSupport.executor(), new CharacterLoreMetrics(),
                getClass().getClassLoader());

        CharacterLoreResult regenerated = service.searchCharacterLore(777L, "서점 영업시간", 3);
        assertThat(regenerated.status()).isEqualTo(LoreStatus.OK);
        assertThat(regenerated.loreKey()).isEqualTo("ren");

        assertThat(service.searchCharacterLore(5L, "서점 영업시간", 3).status()).isEqualTo(LoreStatus.NO_LORE);
        assertThat(service.searchCharacterLore(404L, "서점 영업시간", 3).status()).isEqualTo(LoreStatus.NO_LORE);
        assertThat(service.searchCharacterLore(null, "서점", 3).status()).isEqualTo(LoreStatus.INVALID_INPUT);
    }

    @Test
    void disabledFlagDoesNothing() {
        List<EmbeddingProvider> created = new ArrayList<>();
        CharacterLoreService service = LoreTestSupport.service(false, () -> {
            CountingProvider p = new CountingProvider();
            created.add(p);
            return p;
        }, 0.0, 1_000, new CharacterLoreMetrics());

        assertThat(service.searchCharacterLore(1L, ARIA_Q, 3).status()).isEqualTo(LoreStatus.DISABLED);
        service.warmUpAsync();
        assertThat(created).isEmpty();
        assertThat(service.summary().enabled()).isFalse();
        assertThat(service.summary().searches()).isZero();
    }

    @Test
    void indexIsBuiltOncePerCharacterAndReused() {
        List<CountingProvider> providers = new ArrayList<>();
        CharacterLoreService service = LoreTestSupport.service(true, () -> {
            CountingProvider p = new CountingProvider();
            providers.add(p);
            return p;
        }, 0.0, 5_000, new CharacterLoreMetrics());

        for (int i = 0; i < 10; i++) {
            service.searchCharacterLore(1L, ARIA_Q, 3);
        }
        service.searchCharacterLore(2L, "서점", 3);

        // 캐릭터마다 별도 제공자(코퍼스별 IDF 상태 분리), 캐릭터당 문서 임베딩은 1회뿐.
        assertThat(providers).hasSize(2);
        assertThat(providers).allSatisfy(p -> assertThat(p.documentCalls.get()).isEqualTo(1));
        assertThat(providers.get(0).queryCalls.get()).isEqualTo(10);

        var states = service.summary().indexes();
        assertThat(states.get("aria").state()).isEqualTo("READY");
        assertThat(states.get("ren").state()).isEqualTo("READY");
        assertThat(states.get("noah").state()).isEqualTo("NOT_BUILT");
        assertThat(states.get("aria").semantic()).isFalse();
    }

    @Test
    void timeoutFallsBackWithoutThrowing() {
        CountingProvider slow = new CountingProvider();
        slow.queryDelayMs = 500;
        CharacterLoreMetrics metrics = new CharacterLoreMetrics();
        CharacterLoreService service = LoreTestSupport.service(true, () -> slow, 0.0, 50, metrics);

        long start = System.nanoTime();
        CharacterLoreResult r = service.searchCharacterLore(1L, ARIA_Q, 3);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(r.status()).isEqualTo(LoreStatus.TIMEOUT);
        assertThat(r.hits()).isEmpty();
        assertThat(elapsedMs).isLessThan(400);
        assertThat(service.summary().byStatus()).containsEntry("TIMEOUT", 1L);
    }

    @Test
    void providerFailureBecomesGenericError() {
        CountingProvider failing = new CountingProvider();
        failing.failQuery = true;
        CharacterLoreService service = LoreTestSupport.service(true, () -> failing, 0.0, 5_000, new CharacterLoreMetrics());

        CharacterLoreResult r = service.searchCharacterLore(1L, ARIA_Q, 3);
        assertThat(r.status()).isEqualTo(LoreStatus.ERROR);
        assertThat(r.hits()).isEmpty();
        assertThat(r.toString()).doesNotContain("secret-host");
    }

    @Test
    void missingLoreDocumentIsHandledAsNoLore() {
        CharacterLoreCatalog catalog = new CharacterLoreCatalog(List.of(new CharacterLoreCatalog.Entry(
                "ghost", "유령 (Ghost)", "characters/ghost/lore.md", "유령 세계관")));
        CharacterLoreService service = new CharacterLoreService(true, catalog,
                id -> Optional.of("유령 (Ghost)"), LocalHashingEmbeddingProvider::new, 0.0, 5_000,
                LoreTestSupport.executor(), new CharacterLoreMetrics(), getClass().getClassLoader());

        CharacterLoreResult r = service.searchCharacterLore(1L, "아무 질문", 3);
        assertThat(r.status()).isEqualTo(LoreStatus.NO_LORE);
        assertThat(service.summary().indexes().get("ghost").state()).isEqualTo("EMPTY");
    }

    @Test
    void invalidQueryIsRejectedWithoutEmbedding() {
        CountingProvider p = new CountingProvider();
        CharacterLoreService service = LoreTestSupport.service(true, () -> p, 0.0, 5_000, new CharacterLoreMetrics());
        assertThat(service.searchCharacterLore(1L, "  ", 3).status()).isEqualTo(LoreStatus.INVALID_INPUT);
        assertThat(service.searchCharacterLore(1L, "가".repeat(301), 3).status()).isEqualTo(LoreStatus.INVALID_INPUT);
        assertThat(p.queryCalls.get()).isZero();
    }

    @Test
    void metricsRecordNoQueryContent() {
        CharacterLoreMetrics metrics = new CharacterLoreMetrics();
        CharacterLoreService service = LoreTestSupport.service(true, LocalHashingEmbeddingProvider::new, 0.08, 5_000, metrics);
        service.searchCharacterLore(1L, "비밀스러운 사용자 질문 함장", 3);
        service.searchCharacterLore(2L, "전혀 상관없는 우주 요리", 3);

        CharacterLoreMetrics.Summary summary = service.summary();
        assertThat(summary.searches()).isEqualTo(2);
        assertThat(summary.byCharacter()).containsKeys("aria", "ren");
        assertThat(summary.p50LatencyMs()).isNotNull();
        assertThat(summary.p95LatencyMs()).isNotNull();
        assertThat(summary.toString()).doesNotContain("비밀스러운").doesNotContain("우주 요리");
    }

    @Test
    void hitsCarryTitleSourceTextAndScore() {
        CharacterLoreService service = LoreTestSupport.local(0.0);
        KnowledgeSearchResult.Hit hit = service.searchCharacterLore(1L, "초광속 도약", 1).hits().get(0);
        assertThat(hit.title()).isEqualTo("아리아 세계관");
        assertThat(hit.source()).isEqualTo("characters/aria/lore.md");
        assertThat(hit.text()).isNotBlank();
        assertThat(hit.score()).isPositive();
    }
}
