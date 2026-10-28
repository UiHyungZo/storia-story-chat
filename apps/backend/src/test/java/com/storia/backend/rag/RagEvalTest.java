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
        assertThat(questions).hasSize(28);

        // 같은 질문 세트로 어휘 근거 검사를 끈 경우(이전 동작)와 켠 경우(운영 설정)를 비교한다.
        Map<String, Object> before = RagEvaluator.evaluate(new LocalHashingEmbeddingProvider(), repoRoot, questions, null, false);
        String beforeId = RagEvaluator.writeReport(Path.of("build/eval-reports"), before);
        Map<String, Object> result = RagEvaluator.evaluate(new LocalHashingEmbeddingProvider(), repoRoot, questions, null, true);
        String runId = RagEvaluator.writeReport(Path.of("build/eval-reports"), result);

        Map<String, Object> all = (Map<String, Object>) result.get("all");
        Map<String, Object> integrity = (Map<String, Object>) result.get("source_integrity");
        System.out.println("RAG_EVAL guard=off " + beforeId + " cal=" + before.get("calibration") + " holdout=" + before.get("holdout"));
        System.out.println("RAG_EVAL guard=on  " + runId + " cal=" + result.get("calibration") + " holdout=" + result.get("holdout"));
        System.out.println("RAG_EVAL integrity=" + integrity + " latency=" + result.get("search_latency_ms")
                + " usage=" + result.get("embedding_usage"));

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
        assertThat((Double) all.get("current_doc_hit_at_3_unfiltered")).isGreaterThanOrEqualTo(MIN_CURRENT_DOC_HIT_AT_3);

        // 어휘 검사는 순위를 바꾸지 않고, 범위 내 질문의 근거를 버리지 않으며, 범위 밖 오탐을 늘리지 않는다.
        for (String split : List.of("all", "calibration", "holdout")) {
            Map<String, Object> on = (Map<String, Object>) result.get(split);
            Map<String, Object> off = (Map<String, Object>) before.get(split);
            assertThat(on.get("hit_at_3_unfiltered")).as(split).isEqualTo(off.get("hit_at_3_unfiltered"));
            assertThat(on.get("in_scope_false_no_evidence_rate")).as(split).isEqualTo(off.get("in_scope_false_no_evidence_rate"));
            assertThat((Double) on.get("out_of_scope_false_evidence_rate")).as(split)
                    .isLessThanOrEqualTo((Double) off.get("out_of_scope_false_evidence_rate"));
        }
        // calibration에 넣은 실제 실패 사례("결제 시스템", "payment")는 근거 없음으로 끝나야 한다.
        assertThat(((Map<String, Object>) result.get("calibration")).get("out_of_scope_false_evidence_rate")).isEqualTo(0.0);
    }

    // 2026-10-29 관찰값(28문항, 어휘 검사 켬): Hit@5 0.9474, Hit@3 0.8947, 범위 밖 거절 0.7778, 현재 문서 Hit@3 0.8182
    // (각각 질문 1개 분량의 여유를 둠). holdout 범위 밖 오탐 0.4(2/5)는 남아 있음 — TODO.md 참고.
    static final double MIN_HIT_AT_5 = 0.89;
    static final double MIN_HIT_AT_3 = 0.84;
    static final double MIN_OOS_REJECT = 0.66;
    static final double MIN_CURRENT_DOC_HIT_AT_3 = 0.72;
}
