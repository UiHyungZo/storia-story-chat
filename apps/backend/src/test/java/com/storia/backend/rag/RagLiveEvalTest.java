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
 * 호출량: 인덱스 조각 약 280개(문서 임베딩, 50개씩 배치 → 약 6회 요청, 약 15만 자) + 질의 20회.
 * 임베딩 캐시(build/rag-live-cache)로 재실행 시 문서 임베딩은 다시 호출하지 않는다.
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

        EmbeddingProvider provider = new CachingEmbeddingProvider(
                new GeminiEmbeddingProvider(apiKey, "https://generativelanguage.googleapis.com", model, 768),
                Path.of("build/rag-live-cache"));
        Double minScore = System.getenv("EVAL_RAG_MIN_SCORE") == null ? null : Double.valueOf(System.getenv("EVAL_RAG_MIN_SCORE"));

        Map<String, Object> result = RagEvaluator.evaluate(provider, repoRoot, RagEvaluator.loadQuestions(), minScore);
        String runId = RagEvaluator.writeReport(Path.of("build/eval-reports"), result);
        System.out.println("rag live report: build/eval-reports/" + runId + ".md");
        assertThat(Files.exists(Path.of("build/eval-reports", runId + ".md"))).isTrue();
        assertThat(List.of(result.get("embedding_provider"))).isNotEmpty();
    }
}
