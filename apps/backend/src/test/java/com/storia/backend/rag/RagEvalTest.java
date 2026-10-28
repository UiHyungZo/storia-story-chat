package com.storia.backend.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 실제 프로젝트 문서(허용 목록 14개)에 대한 RAG 검색 평가 — 로컬 해싱 벡터(외부 호출 없음, 의미 임베딩 아님).
 * 수치는 실행 결과에서 계산해 build/eval-reports/rag-eval-*.{json,md}에 기록한다. 아래 단언은 관찰된 결과에서 여유를
 * 둔 회귀 방지선이지 목표 성능이 아니다. 문서나 청킹을 바꿔 수치가 달라지면 리포트를 보고 방지선을 갱신한다.
 */
class RagEvalTest {

    @Test
    @SuppressWarnings("unchecked")
    void evaluateRetrievalOnRealDocuments_andWriteReport() throws Exception {
        Path repoRoot = Path.of("../..").toAbsolutePath().normalize();
        assertThat(Files.isRegularFile(repoRoot.resolve("AGENTS.md"))).as("저장소 루트: " + repoRoot).isTrue();
        List<RagEvaluator.Question> questions = RagEvaluator.loadQuestions();
        assertThat(questions).hasSize(20);

        Map<String, Object> result = RagEvaluator.evaluate(new LocalHashingEmbeddingProvider(), repoRoot, questions, null);
        String runId = RagEvaluator.writeReport(Path.of("build/eval-reports"), result);

        Map<String, Object> all = (Map<String, Object>) result.get("all");
        Map<String, Object> integrity = (Map<String, Object>) result.get("source_integrity");
        System.out.println("RAG_EVAL " + runId + " all=" + all + " integrity=" + integrity + " latency=" + result.get("search_latency_ms"));

        assertThat(result.get("documents")).isEqualTo(14);
        assertThat((Integer) result.get("chunks")).isGreaterThan(100);
        // 출처 무결성은 결정적이어야 한다: 모든 결과의 출처는 허용 목록 안이고 발췌는 원문에 실제로 존재한다.
        assertThat(integrity.get("source_in_allowlist")).isEqualTo(integrity.get("returned_hits"));
        assertThat(integrity.get("text_verbatim_in_source_doc")).isEqualTo(integrity.get("returned_hits"));
        assertThat((Integer) integrity.get("returned_hits")).isGreaterThan(0);
        // 회귀 방지선 (관찰 결과 기반 — 아래 값은 첫 실행 리포트를 보고 확정)
        assertThat((Double) all.get("hit_at_5_unfiltered")).isGreaterThanOrEqualTo(MIN_HIT_AT_5);
        assertThat((Double) all.get("hit_at_3_unfiltered")).isGreaterThanOrEqualTo(MIN_HIT_AT_3);
        assertThat((Double) all.get("out_of_scope_correct_no_evidence_rate")).isGreaterThanOrEqualTo(MIN_OOS_REJECT);
    }

    // 2026-10-28 첫 실행 관찰값: Hit@5 0.9375, Hit@3 0.875, 범위 밖 거절 1.0 (각각 질문 1개 분량의 여유를 둠)
    static final double MIN_HIT_AT_5 = 0.875;
    static final double MIN_HIT_AT_3 = 0.8125;
    static final double MIN_OOS_REJECT = 0.75;
}
