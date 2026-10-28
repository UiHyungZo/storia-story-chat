package com.storia.backend.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvalRunnerTest {

    private static EvalScenario scenario(String review, List<Check> checks) {
        return new EvalScenario("t-1", "sys", List.of(), "입력", "기대", List.of("r"), List.of("tag"), review, checks);
    }

    private static EvalResult run(String reply, EvalScenario s) {
        return new EvalRunner(new FakeReplyGenerator(in -> reply)).run(List.of(s)).get(0);
    }

    @Test
    void mechanicalScenario_passesWhenAllChecksPass() {
        EvalResult r = run("선장님 안녕하세요", scenario("mechanical", List.of(new Check("contains_any", List.of("선장님"), null))));
        assertThat(r.verdict()).isEqualTo(Verdict.PASS);
    }

    @Test
    void failedCheck_isFail() {
        EvalResult r = run("안녕", scenario("mechanical", List.of(new Check("contains_any", List.of("선장님"), null))));
        assertThat(r.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(r.failedChecks()).hasSize(1);
    }

    @Test
    void humanReviewScenario_neverAutoPasses() {
        EvalResult r = run("네", scenario("human", List.of(new Check("not_empty", null, null))));
        assertThat(r.verdict()).isEqualTo(Verdict.UNDETERMINED);
    }

    @Test
    void humanReviewScenario_failsWhenMechanicalCheckFails() {
        EvalResult r = run("네", scenario("human", List.of(new Check("max_chars", null, 0))));
        assertThat(r.verdict()).isEqualTo(Verdict.FAIL);
    }

    @Test
    void noChecks_isUndetermined() {
        assertThat(run("네", scenario("mechanical", List.of())).verdict()).isEqualTo(Verdict.UNDETERMINED);
    }

    @Test
    void blankReply_isFail() {
        assertThat(run("  ", scenario("mechanical", List.of(new Check("not_empty", null, null)))).verdict())
                .isEqualTo(Verdict.FAIL);
    }

    @Test
    void generatorException_isFailNotCrash() {
        ReplyGenerator broken = new FakeReplyGenerator(in -> null) {
            @Override
            public String generate(String s, List<ChatTurn> h, String i) {
                throw new IllegalStateException("boom");
            }
        };
        EvalResult r = new EvalRunner(broken).run(List.of(scenario("mechanical", List.of()))).get(0);
        assertThat(r.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(r.reason()).startsWith("generation_error");
    }

    @Test
    void unknownCheckType_failsRatherThanPasses() {
        EvalResult r = run("네", scenario("mechanical", List.of(new Check("bogus", null, null))));
        assertThat(r.verdict()).isEqualTo(Verdict.FAIL);
    }

    @Test
    void notContainsAny_andCharLimits_useCodePoints() {
        assertThat(ScenarioChecks.evaluate(new Check("not_contains_any", List.of("금지"), null), "금지어")).isNotNull();
        assertThat(ScenarioChecks.evaluate(new Check("max_chars", null, 3), "😭😭😭")).isNull();
        assertThat(ScenarioChecks.evaluate(new Check("has_hangul", null, null), "abc")).isNotNull();
    }
}
