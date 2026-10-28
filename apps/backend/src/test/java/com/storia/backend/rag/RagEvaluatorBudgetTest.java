package com.storia.backend.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 원격 평가의 호출 상한과 첫 실패 중단(재시도 없음)을 외부 호출 없이 확인한다. */
class RagEvaluatorBudgetTest {

    private static final Path REPO_ROOT = Path.of("../..").toAbsolutePath().normalize();

    /** 문서 임베딩은 성공하고 질의 임베딩은 항상 실패하는 provider. 하위 호출 수를 센다. */
    private static final class FailingQueries extends LocalHashingEmbeddingProvider {
        final AtomicInteger queryCalls = new AtomicInteger();

        @Override
        public float[] embedQuery(String text) {
            queryCalls.incrementAndGet();
            throw new IllegalStateException("임베딩 호출 실패: HTTP 429");
        }
    }

    @Test
    void firstQueryFailure_abortsEvaluationWithoutFurtherRemoteCalls() throws Exception {
        FailingQueries failing = new FailingQueries();
        List<RagEvaluator.Question> questions = RagEvaluator.loadQuestions();

        assertThatThrownBy(() -> RagEvaluator.evaluate(
                new RagEvaluator.MeteredEmbeddingProvider(failing, 300, questions.size()), REPO_ROOT, questions, null, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("평가 중단");
        assertThat(failing.queryCalls.get()).isEqualTo(1);
    }

    @Test
    void documentBudgetExceeded_abortsBeforeCallingProvider() {
        AtomicInteger documentCalls = new AtomicInteger();
        LocalHashingEmbeddingProvider counting = new LocalHashingEmbeddingProvider() {
            @Override
            public List<float[]> embedDocuments(List<String> texts) {
                documentCalls.incrementAndGet();
                return super.embedDocuments(texts);
            }
        };

        assertThatThrownBy(() -> RagEvaluator.evaluate(new RagEvaluator.MeteredEmbeddingProvider(counting, 10, 28),
                REPO_ROOT, RagEvaluator.loadQuestions(), null, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("상한");
        assertThat(documentCalls.get()).isZero();
    }

    @Test
    void withinBudget_eachDistinctQueryIsEmbeddedOnce() throws Exception {
        List<RagEvaluator.Question> questions = RagEvaluator.loadQuestions();
        RagEvaluator.MeteredEmbeddingProvider metered =
                new RagEvaluator.MeteredEmbeddingProvider(new LocalHashingEmbeddingProvider(), 300, questions.size());

        RagEvaluator.evaluate(metered, REPO_ROOT, questions, null, true);

        assertThat(metered.queryRequests).isEqualTo(questions.size());
        assertThat(metered.documentRequests).isEqualTo(1);
        assertThat(metered.documentTexts).isLessThanOrEqualTo(300);
    }
}
