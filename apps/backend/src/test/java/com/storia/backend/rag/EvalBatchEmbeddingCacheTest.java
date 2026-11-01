package com.storia.backend.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 평가 전용 배치 캐시 — 로컬 Mock HTTP 서버만 사용한다(실제 Gemini 호출 없음). */
class EvalBatchEmbeddingCacheTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String KEY = "test-key-must-not-leak";
    private static final String SECRET_ERROR_TEXT = "Quota exceeded for DOC-CONTENT-SECRET";

    private HttpServer server;
    /** 배치 요청마다 받은 항목 수. */
    private final List<Integer> batchSizes = new CopyOnWriteArrayList<>();
    private final AtomicInteger queryCalls = new AtomicInteger();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * {@code failFromBatch}번째(1부터) 배치 요청부터 429를 돌려준다(0이면 실패 없음). {@code dims}개 값의 벡터를 돌려준다.
     */
    private String start(int failFromBatch, int dims) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger batches = new AtomicInteger();
        server.createContext("/", exchange -> {
            JsonNode body = MAPPER.readTree(exchange.getRequestBody().readAllBytes());
            String path = exchange.getRequestURI().getPath();
            int status = 200;
            String json;
            if (path.endsWith(":embedContent")) {
                queryCalls.incrementAndGet();
                json = "{\"embedding\":{\"values\":" + values(dims, 1) + "}}";
            } else {
                int n = batches.incrementAndGet();
                int size = body.path("requests").size();
                batchSizes.add(size);
                if (failFromBatch > 0 && n >= failFromBatch) {
                    status = 429;
                    exchange.getResponseHeaders().add("Retry-After", "30");
                    json = "{\"error\":{\"code\":429,\"message\":\"" + SECRET_ERROR_TEXT + "\",\"status\":\"RESOURCE_EXHAUSTED\","
                            + "\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\":\"30s\"}]}}";
                } else {
                    StringBuilder sb = new StringBuilder("{\"embeddings\":[");
                    for (int i = 0; i < size; i++) {
                        sb.append(i == 0 ? "" : ",").append("{\"values\":").append(values(dims, n * 1000 + i + 1)).append('}');
                    }
                    json = sb.append("]}").toString();
                }
            }
            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String values(int dims, int seed) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < dims; i++) {
            sb.append(i == 0 ? "" : ",").append(i == 0 ? seed : 1);
        }
        return sb.append(']').toString();
    }

    private static List<String> texts(int n) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add("문서 조각 " + i);
        }
        return list;
    }

    private static GeminiEmbeddingProvider gemini(String url, String model, int dims) {
        return new GeminiEmbeddingProvider(KEY, url, model, dims);
    }

    @Test
    void partialSuccessThen429_stopsImmediately_andRerunReusesSucceededBatches(@TempDir Path dir) throws Exception {
        List<String> texts = texts(130); // 50 + 50 + 30 → 3배치
        String url = start(3, 3);
        EvalBatchEmbeddingCache first = new EvalBatchEmbeddingCache(gemini(url, "gemini-embedding-2", 3), dir, 0);

        assertThatThrownBy(() -> first.embedDocuments(texts))
                .isInstanceOf(EvalBatchEmbeddingCache.BatchAbortException.class)
                .hasMessageContaining("3/3").hasMessageContaining("HTTP 429").hasMessageContaining("RESOURCE_EXHAUSTED")
                .hasMessageContaining("Retry-After=30")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(SECRET_ERROR_TEXT).doesNotContain(KEY)
                        .doesNotContain("문서 조각"));
        EvalBatchEmbeddingCache.BatchFailure f = first.lastFailure;
        assertThat(f.batchNumber()).isEqualTo(3);
        assertThat(f.totalBatches()).isEqualTo(3);
        assertThat(f.batchSize()).isEqualTo(30);
        assertThat(f.httpStatus()).isEqualTo(429);
        assertThat(f.errorType()).isEqualTo("rate_limited");
        assertThat(f.errorStatus()).isEqualTo("RESOURCE_EXHAUSTED");
        assertThat(f.retryAfter()).isEqualTo("30");
        assertThat(f.retryDelay()).isEqualTo("30s");
        assertThat(f.succeededBatchesThisRun()).isEqualTo(2);
        assertThat(f.remainingTexts()).isEqualTo(30);
        assertThat(f.remainingBatches()).isEqualTo(1);
        assertThat(batchSizes).containsExactly(50, 50, 30); // 실패한 배치를 다시 보내지 않음(자동 재시도 없음)
        assertThat(f.toString()).doesNotContain(SECRET_ERROR_TEXT).doesNotContain(KEY);
        String saved = Files.readString(first.file());
        assertThat(saved).doesNotContain("문서 조각").doesNotContain(KEY);
        assertThat(MAPPER.readTree(saved).path("vectors").size()).isEqualTo(100);

        // 재실행: 한도가 풀린 서버, 새 인스턴스(디스크에서 다시 읽음) → 실패했던 마지막 배치만 호출한다.
        server.stop(0);
        batchSizes.clear();
        String url2 = start(0, 3);
        EvalBatchEmbeddingCache second = new EvalBatchEmbeddingCache(gemini(url2, "gemini-embedding-2", 3), dir, 0);
        List<float[]> vectors = second.embedDocuments(texts);

        assertThat(batchSizes).containsExactly(30);
        assertThat(second.reusedTexts).isEqualTo(100);
        assertThat(second.remoteBatchCalls).isEqualTo(1);
        assertThat(vectors).hasSize(130).allSatisfy(v -> assertThat(v).hasSize(3));
        assertThat(second.cachedTexts()).isEqualTo(130);
        // 마지막 성공 응답의 형식 정보(벡터·텍스트 없음)
        GeminiEmbeddingProvider probe = gemini(url2, "gemini-embedding-2", 3);
        probe.embedDocuments(List.of("a"));
        assertThat(probe.lastResponseInfo().status()).isEqualTo(200);
        assertThat(probe.lastResponseInfo().topLevelFields()).containsExactly("embeddings");
        assertThat(probe.lastResponseInfo().usage()).isEmpty();
        batchSizes.clear();

        // 세 번째 실행은 원격 호출이 없다.
        batchSizes.clear();
        new EvalBatchEmbeddingCache(gemini(url2, "gemini-embedding-2", 3), dir, 0).embedDocuments(texts);
        assertThat(batchSizes).isEmpty();
    }

    @Test
    void firstBatchFailure_cachesNothing_andReportsAllBatchesRemaining(@TempDir Path dir) throws Exception {
        String url = start(1, 3);
        EvalBatchEmbeddingCache cache = new EvalBatchEmbeddingCache(gemini(url, "gemini-embedding-2", 3), dir, 0);

        assertThatThrownBy(() -> cache.embedDocuments(texts(283))).isInstanceOf(EvalBatchEmbeddingCache.BatchAbortException.class);

        assertThat(batchSizes).containsExactly(50);
        assertThat(cache.lastFailure.succeededBatchesThisRun()).isZero();
        assertThat(cache.lastFailure.remainingTexts()).isEqualTo(283);
        assertThat(cache.lastFailure.remainingBatches()).isEqualTo(6);
        assertThat(Files.exists(cache.file())).isFalse();
    }

    @Test
    void cacheKeyReflectsModelDimensionsAndRequestFormat(@TempDir Path dir) throws Exception {
        String url = start(0, 3);
        List<String> texts = texts(10);
        new EvalBatchEmbeddingCache(gemini(url, "gemini-embedding-2", 3), dir, 0).embedDocuments(texts);
        assertThat(batchSizes).containsExactly(10);

        // 같은 텍스트라도 다른 모델(요청 형식: 접두어 ↔ taskType)이면 재사용하지 않는다.
        EvalBatchEmbeddingCache otherModel = new EvalBatchEmbeddingCache(gemini(url, "gemini-embedding-001", 3), dir, 0);
        otherModel.embedDocuments(texts);
        assertThat(batchSizes).containsExactly(10, 10);
        assertThat(otherModel.reusedTexts).isZero();

        EvalBatchEmbeddingCache a = new EvalBatchEmbeddingCache(gemini(url, "gemini-embedding-2", 3), dir, 0);
        EvalBatchEmbeddingCache b = new EvalBatchEmbeddingCache(gemini(url, "gemini-embedding-2", 4), dir, 0);
        assertThat(a.key("x")).isNotEqualTo(b.key("x"));
        assertThat(a.file()).isNotEqualTo(b.file());
        assertThat(gemini(url, "gemini-embedding-2", 3).documentRequestSignature())
                .contains("batchEmbedContents").contains("gemini-embedding-2").contains("dims=3").contains("text-prefix");
    }

    @Test
    void responseWithWrongDimensions_isNotCached(@TempDir Path dir) throws Exception {
        String url = start(0, 3); // 서버는 3차원을 돌려주지만 요청은 4차원
        EvalBatchEmbeddingCache cache = new EvalBatchEmbeddingCache(gemini(url, "gemini-embedding-2", 4), dir, 0);

        assertThatThrownBy(() -> cache.embedDocuments(texts(5))).isInstanceOf(EvalBatchEmbeddingCache.BatchAbortException.class);

        assertThat(cache.lastFailure.errorType()).isEqualTo("invalid_response");
        assertThat(cache.cachedTexts()).isZero();
        assertThat(Files.exists(cache.file())).isFalse();
    }

    @Test
    void corruptOrMismatchedCacheFiles_areNotUsed(@TempDir Path dir) throws Exception {
        String url = start(0, 3);
        EvalBatchEmbeddingCache probe = new EvalBatchEmbeddingCache(gemini(url, "gemini-embedding-2", 3), dir, 0);
        Files.writeString(probe.file(), "{not json");
        EvalBatchEmbeddingCache corrupt = new EvalBatchEmbeddingCache(gemini(url, "gemini-embedding-2", 3), dir, 0);
        corrupt.embedDocuments(texts(3));
        assertThat(batchSizes).containsExactly(3);

        // 헤더의 형식 식별자가 다르면(다른 요청 방식으로 만든 파일) 항목을 쓰지 않는다.
        String tampered = Files.readString(probe.file()).replace("gemini-doc-v1", "gemini-doc-v0");
        Files.writeString(probe.file(), tampered);
        EvalBatchEmbeddingCache mismatched = new EvalBatchEmbeddingCache(gemini(url, "gemini-embedding-2", 3), dir, 0);
        assertThat(mismatched.cachedTexts()).isZero();
        assertThat(mismatched.ignoredCacheEntries).isEqualTo(3);
    }

    @Test
    void intervalIsAppliedOnlyBetweenRemoteBatches_andQueriesAreNotCached(@TempDir Path dir) throws Exception {
        String url = start(0, 3);
        List<Long> sleeps = new ArrayList<>();
        EvalBatchEmbeddingCache cache = new EvalBatchEmbeddingCache(gemini(url, "gemini-embedding-2", 3), dir, 1500, sleeps::add);

        cache.embedDocuments(texts(120)); // 3배치 → 간격 2번
        assertThat(sleeps).containsExactly(1500L, 1500L);

        cache.embedDocuments(texts(120)); // 전부 캐시 → 호출·대기 없음
        assertThat(sleeps).hasSize(2);

        cache.embedQuery("질문");
        cache.embedQuery("질문");
        assertThat(queryCalls.get()).isEqualTo(2);
    }

    // ---- 속도 제어 (가짜 시계: 대기하면 시계가 그만큼 흐른다) ----

    private static final class FakeClock {
        long now = 1_000_000;
        final List<Long> waits = new ArrayList<>();

        void sleep(long ms) {
            waits.add(ms);
            now += ms;
        }
    }

    @Test
    void limiter_waitsUntilItemsFitInTheMinuteWindow_andCountsBatchItemsAsRequests() {
        FakeClock clock = new FakeClock();
        EvalRateLimiter limiter = new EvalRateLimiter(80, 1_000_000, () -> clock.now, clock::sleep);

        limiter.acquire(50, 10);
        limiter.acquire(30, 10); // 합 80 — 대기 없음
        assertThat(clock.waits).isEmpty();
        limiter.acquire(1, 10); // 81번째 항목 → 첫 요청이 창에서 빠질 때까지(60초 + 여유 1초)
        assertThat(clock.waits).containsExactly(61_000L);
    }

    @Test
    void limiter_tokenBudget_andSingleRequestOverBudgetIsRefusedWithoutWaiting() {
        FakeClock clock = new FakeClock();
        EvalRateLimiter limiter = new EvalRateLimiter(1_000, 24_000, () -> clock.now, clock::sleep);

        limiter.acquire(10, 20_000);
        limiter.acquire(1, 5_000); // 토큰 합 25,000 > 24,000 → 대기
        assertThat(clock.waits).hasSize(1);

        assertThatThrownBy(() -> limiter.acquire(1, 30_000)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("단일 요청");
        assertThat(clock.waits).hasSize(1);
    }

    @Test
    void batchesArePackedByCharsAndItems_planMatchesWhatIsSent(@TempDir Path dir) throws Exception {
        String url = start(0, 3);
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            texts.add("x".repeat(80) + i); // 실제 입력은 "title: none | text: " 접두어(20자) 포함 101자
        }
        EvalBatchEmbeddingCache cache = new EvalBatchEmbeddingCache(gemini(url, "gemini-embedding-2", 3), dir, 0,
                4, 350, EvalRateLimiter.unlimited(), ms -> { });

        List<int[]> plan = cache.plan(texts);
        cache.embedDocuments(texts);

        assertThat(plan).extracting(p -> p[0]).containsExactly(3, 3, 3, 1); // 101×3=303 ≤ 350, 4개째는 404 > 350
        assertThat(plan).allSatisfy(p -> assertThat(p[1]).isLessThanOrEqualTo(350));
        assertThat(batchSizes).containsExactly(3, 3, 3, 1);
        assertThat(cache.plan(texts)).isEmpty(); // 전부 캐시됨
    }

    @Test
    void documentsAndQueriesShareTheMinuteBudget_queriesWaitAfterALargeLastBatch(@TempDir Path dir) throws Exception {
        String url = start(0, 3);
        FakeClock clock = new FakeClock();
        EvalRateLimiter limiter = new EvalRateLimiter(60, 1_000_000, () -> clock.now, clock::sleep);
        EvalBatchEmbeddingCache cache = new EvalBatchEmbeddingCache(gemini(url, "gemini-embedding-2", 3), dir, 0,
                50, Integer.MAX_VALUE, limiter, clock::sleep);

        cache.embedDocuments(texts(50)); // 항목 50
        for (int i = 0; i < 10; i++) {
            cache.embedQuery("질문 " + i); // 51~60번째 — 대기 없음
        }
        assertThat(clock.waits).isEmpty();
        cache.embedQuery("질문 10"); // 61번째 → 문서 배치가 창에서 빠질 때까지 대기
        assertThat(clock.waits).containsExactly(61_000L);
        assertThat(queryCalls.get()).isEqualTo(11);
    }

    @Test
    void chunkLargerThanTheTokenBudget_isNotSent_andReportedAsOverBudget(@TempDir Path dir) throws Exception {
        String url = start(0, 3);
        EvalBatchEmbeddingCache cache = new EvalBatchEmbeddingCache(gemini(url, "gemini-embedding-2", 3), dir, 0,
                50, 100, new EvalRateLimiter(80, 100, System::currentTimeMillis, ms -> { }), ms -> { });

        assertThatThrownBy(() -> cache.embedDocuments(List.of("y".repeat(200))))
                .isInstanceOf(EvalBatchEmbeddingCache.BatchAbortException.class);

        assertThat(cache.lastFailure.errorType()).isEqualTo("request_over_budget");
        assertThat(batchSizes).isEmpty(); // 원격 호출 없음
    }

    @Test
    void httpFailureDetails_onlyWellFormedValuesAreExtracted() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Retry-After", "<script>" + SECRET_ERROR_TEXT);
            byte[] out = ("not json " + SECRET_ERROR_TEXT).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        GeminiEmbeddingProvider p = gemini("http://127.0.0.1:" + server.getAddress().getPort(), "gemini-embedding-2", 3);

        assertThatThrownBy(() -> p.embedQuery("q"))
                .isInstanceOfSatisfying(EmbeddingHttpException.class, e -> {
                    assertThat(e.status()).isEqualTo(503);
                    assertThat(e.retryAfter()).isNull();
                    assertThat(e.errorStatus()).isNull();
                    assertThat(e.retryDelay()).isNull();
                    assertThat(e.getMessage()).isEqualTo("임베딩 호출 실패: HTTP 503"); // 기존 메시지와 동일
                })
                .isInstanceOf(IllegalStateException.class);
    }
}
