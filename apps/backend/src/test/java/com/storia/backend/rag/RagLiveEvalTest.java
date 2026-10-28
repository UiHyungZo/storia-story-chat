package com.storia.backend.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 실제 Gemini Embedding API로 같은 평가를 수행한다 (외부 유료 호출 — 사용자 승인 후 실행).
 * 호출량(2026-10-29 로컬 평가로 실측한 같은 코퍼스·질문 기준): 문서 조각 283개(약 14.7만 자, 50개씩 배치 → 6회 요청)
 * + 질의 28회(약 700자). 평가기가 같은 질의를 한 번만 임베딩하므로 지연 반복 측정은 추가 호출을 만들지 않는다.
 * 평가 전용 배치 캐시({@link EvalBatchEmbeddingCache}, build/rag-live-cache)는 배치가 성공할 때마다 저장하므로, 중간에
 * 실패해도 재실행 시 성공한 배치는 다시 호출하지 않는다(질의는 매 실행 28회). 실패 시 즉시 중단하고(재시도 없음)
 * build/eval-reports/rag-live-failure-*.json에 배치 번호·HTTP 상태·Retry-After·남은 호출량을 남긴다.
 * 실제 호출 수는 리포트의 embedding_usage에 기록된다.
 *
 * <p>실행: {@code RUN_LIVE_EVAL=true RUN_LIVE_RAG_EVAL=true GEMINI_API_KEY=... ./gradlew evalLive}
 * (두 환경변수가 모두 있어야 한다). 결과 리포트는 build/eval-reports에 저장되며 빌드를 실패시키지 않는다.
 * 점수 분포가 로컬 해싱 벡터와 다르므로 기본 임계값(0.55)은 이 리포트의 임계값 스윕으로 재보정해야 한다.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_EVAL", matches = "true")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_RAG_EVAL", matches = "true")
class RagLiveEvalTest {

    @Test
    void evaluateWithGeminiEmbeddings() throws Exception {
        String apiKey = System.getenv("GEMINI_API_KEY");
        assertThat(apiKey).as("GEMINI_API_KEY 필요").isNotBlank();
        String model = System.getenv().getOrDefault("EVAL_EMBEDDING_MODEL", "gemini-embedding-2");
        Path repoRoot = Path.of("../..").toAbsolutePath().normalize();

        // 배치마다 저장하는 평가 전용 캐시. 배치 사이 간격은 EVAL_EMBEDDING_BATCH_INTERVAL_MS(기본 0). 자동 재시도 없음.
        long intervalMs = Long.parseLong(System.getenv().getOrDefault("EVAL_EMBEDDING_BATCH_INTERVAL_MS", "0"));
        EvalBatchEmbeddingCache provider = new EvalBatchEmbeddingCache(
                new GeminiEmbeddingProvider(apiKey, "https://generativelanguage.googleapis.com", model, 768),
                Path.of("build/rag-live-cache"), intervalMs);
        Double minScore = System.getenv("EVAL_RAG_MIN_SCORE") == null ? null : Double.valueOf(System.getenv("EVAL_RAG_MIN_SCORE"));

        // 호출 상한: 문서 임베딩 최대 6배치(300개), 질의 임베딩은 질문 수만큼. 첫 실패에서 중단하고 재시도하지 않는다.
        List<RagEvaluator.Question> questions = RagEvaluator.loadQuestions();
        RagEvaluator.MeteredEmbeddingProvider metered = new RagEvaluator.MeteredEmbeddingProvider(
                provider, 6 * GeminiEmbeddingProvider.BATCH_SIZE, questions.size());
        Map<String, Object> result;
        try {
            result = RagEvaluator.evaluate(metered, repoRoot, questions, minScore, true);
        } catch (RuntimeException e) {
            Path report = writeFailureReport(provider, metered, questions.size());
            System.out.println("rag live FAILED: " + e.getMessage() + " — " + report);
            throw e;
        }
        String runId = RagEvaluator.writeReport(Path.of("build/eval-reports"), result);
        System.out.println("rag live report: build/eval-reports/" + runId + ".md");
        assertThat(Files.exists(Path.of("build/eval-reports", runId + ".md"))).isTrue();
        assertThat(List.of(result.get("embedding_provider"))).isNotEmpty();
    }

    /**
     * 실패 리포트: 안전한 진단 값과 호출량만 기록한다(키·요청 본문·문서/질의 내용·오류 메시지 문장 없음).
     * 재실행 시 예상 호출량 = 남은 문서 배치 + 질의 전체(질의는 캐시하지 않음).
     */
    static Path writeFailureReport(EvalBatchEmbeddingCache cache, RagEvaluator.MeteredEmbeddingProvider metered,
                                   int questionCount) throws java.io.IOException {
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("failed_at", java.time.Instant.now().toString());
        r.put("embedding_provider", cache.id());
        EvalBatchEmbeddingCache.BatchFailure f = cache.lastFailure;
        r.put("phase", f != null ? "documents" : "queries");
        if (f != null) {
            r.put("document_batch_failure", f);
        } else if (metered.lastError instanceof EmbeddingHttpException h) {
            r.put("query_failure", Map.of("http_status", h.status(),
                    "error_status", String.valueOf(h.errorStatus()), "retry_after", String.valueOf(h.retryAfter()),
                    "retry_delay", String.valueOf(h.retryDelay()), "query_requests_before_failure", metered.queryRequests));
        } else {
            r.put("error_type", metered.lastError == null ? "unknown" : metered.lastError.getClass().getSimpleName());
        }
        r.put("remote_document_batch_calls_this_run", cache.remoteBatchCalls);
        r.put("document_texts_reused_from_cache", cache.reusedTexts);
        r.put("document_texts_cached_now", cache.cachedTexts());
        r.put("query_embedding_requests_this_run", metered.queryRequests);
        r.put("estimated_calls_on_rerun", Map.of(
                "document_batches", f != null ? f.remainingBatches() : 0,
                "query_embeddings", questionCount));
        Path dir = Path.of("build/eval-reports");
        Files.createDirectories(dir);
        Path out = dir.resolve("rag-live-failure-" + System.currentTimeMillis() + ".json");
        Files.writeString(out, new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValueAsString(r));
        return out;
    }
}
