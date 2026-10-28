package com.storia.backend.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 실제 Gemini Embedding API 최초 검증 — <b>문서 배치 1회만</b> 호출한다 (외부 유료 호출, 사용자 승인 후 실행).
 * 전체 코퍼스에서 고르게 뽑은 {@value #SAMPLE}개 조각을 한 배치로 보내고 질의 임베딩은 호출하지 않는다.
 * 응답 형식·HTTP 상태·임베딩 개수와 차원·{@code usageMetadata}(있으면 실제 토큰 수)를 기록하고, 성공한 벡터는 전체 평가와
 * 같은 배치 캐시(build/rag-live-cache)에 저장한다 — 이후 전체 평가는 이 조각을 다시 호출하지 않는다. 실패하면 즉시 중단(재시도 없음).
 *
 * <p>실행: {@code RUN_LIVE_EVAL=true RUN_LIVE_RAG_PROBE=true GEMINI_API_KEY=... ./gradlew evalLive --tests
 * 'com.storia.backend.rag.RagLiveBatchProbeTest'}
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_EVAL", matches = "true")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_RAG_PROBE", matches = "true")
class RagLiveBatchProbeTest {

    static final int SAMPLE = 10;

    @Test
    void singleDocumentBatch() throws Exception {
        String apiKey = System.getenv("GEMINI_API_KEY");
        assertThat(apiKey).as("GEMINI_API_KEY 필요").isNotBlank();
        Path repoRoot = Path.of("../..").toAbsolutePath().normalize();

        List<String> all = RagLiveEvalTest.documentTexts(repoRoot);
        List<String> sample = new ArrayList<>();
        int step = all.size() / SAMPLE;
        for (int i = 0; i < SAMPLE; i++) {
            sample.add(all.get(i * step));
        }

        GeminiEmbeddingProvider gemini = new GeminiEmbeddingProvider(apiKey, "https://generativelanguage.googleapis.com",
                "gemini-embedding-2", 768);
        EvalBatchEmbeddingCache cache = new EvalBatchEmbeddingCache(gemini, Path.of("build/rag-live-cache"), 0,
                SAMPLE, 24_000, new EvalRateLimiter(80, 24_000, System::currentTimeMillis, ms -> { }), ms -> { });
        List<int[]> plan = cache.plan(sample);
        // 사전 점검: 최대 1배치. 이미 캐시돼 있으면 0배치(호출 없음).
        assertThat(plan.size()).as("프로브는 배치 1회만 허용").isLessThanOrEqualTo(1);
        int inputChars = sample.stream().mapToInt(t -> gemini.documentText(t).length()).sum();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("at", java.time.Instant.now().toString());
        r.put("model", "gemini-embedding-2");
        r.put("dims_requested", 768);
        r.put("cached_before", cache.cachedTexts());
        r.put("planned_batches", plan.size());
        r.put("sample_items", sample.size());
        r.put("sample_input_chars", inputChars);
        r.put("corpus_chunks", all.size());
        try {
            List<float[]> vectors = cache.embedDocuments(sample);
            GeminiEmbeddingProvider.ResponseInfo info = gemini.lastResponseInfo();
            r.put("remote_batch_calls", cache.remoteBatchCalls);
            r.put("http_status", info == null ? null : info.status());
            r.put("response_top_level_fields", info == null ? null : info.topLevelFields());
            r.put("usage_metadata", info == null ? null : info.usage());
            r.put("embeddings_returned", vectors.size());
            r.put("vector_dims", vectors.stream().map(v -> v.length).distinct().toList());
            Long tokens = info == null ? null : info.usage().get("promptTokenCount");
            r.put("chars_per_token", tokens == null || tokens == 0 ? null : Math.round(inputChars * 1000.0 / tokens) / 1000.0);
            r.put("cached_after", cache.cachedTexts());
        } catch (EvalBatchEmbeddingCache.BatchAbortException e) {
            r.put("remote_batch_calls", cache.remoteBatchCalls);
            r.put("failure", e.failure);
            write(r);
            throw e;
        }
        Path out = write(r);
        System.out.println("rag live probe: " + out + " " + r);
        assertThat(cache.remoteBatchCalls).isLessThanOrEqualTo(1);
    }

    private static Path write(Map<String, Object> r) throws java.io.IOException {
        Path dir = Path.of("build/eval-reports");
        Files.createDirectories(dir);
        Path out = dir.resolve("rag-live-probe-" + System.currentTimeMillis() + ".json");
        Files.writeString(out, new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(r));
        return out;
    }
}
