package com.storia.backend.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * 시나리오를 {@link ReplyGenerator}로 실행하고 pass/fail/undetermined로 판정한다.
 * 운영 서비스·Repository에 의존하지 않는다(생성자 인자는 생성기 하나뿐).
 *
 * <p>판정 규칙: 생성 실패·빈 응답·기계 검증 실패 → FAIL. 기계 검증을 모두 통과해도
 * review가 "human"이거나 검증 조건이 없으면 UNDETERMINED (자동으로 성공 처리하지 않는다).
 */
public class EvalRunner {

    private final ReplyGenerator generator;

    public EvalRunner(ReplyGenerator generator) {
        this.generator = generator;
    }

    public List<EvalResult> run(List<EvalScenario> scenarios) {
        List<EvalResult> results = new ArrayList<>();
        for (EvalScenario scenario : scenarios) {
            results.add(runOne(scenario));
        }
        return results;
    }

    EvalResult runOne(EvalScenario s) {
        String reply;
        try {
            reply = generator.generate(s.systemPrompt(), s.history(), s.input());
        } catch (Exception e) {
            return result(s, Verdict.FAIL, "generation_error: " + e.getClass().getSimpleName(), List.of(), null);
        }
        if (reply == null || reply.isBlank()) {
            return result(s, Verdict.FAIL, "빈 응답", List.of("empty_reply"), reply);
        }

        List<String> failed = new ArrayList<>();
        for (Check check : s.checks() == null ? List.<Check>of() : s.checks()) {
            String failure = ScenarioChecks.evaluate(check, reply);
            if (failure != null) {
                failed.add(failure);
            }
        }
        if (!failed.isEmpty()) {
            return result(s, Verdict.FAIL, "기계 검증 실패", failed, reply);
        }
        if ("human".equals(s.review())) {
            return result(s, Verdict.UNDETERMINED, "기계 검증 통과, 사람의 rubric 검토 필요", failed, reply);
        }
        if (s.checks() == null || s.checks().isEmpty()) {
            return result(s, Verdict.UNDETERMINED, "기계 검증 조건 없음", failed, reply);
        }
        return result(s, Verdict.PASS, "기계 검증 통과", failed, reply);
    }

    private EvalResult result(EvalScenario s, Verdict v, String reason, List<String> failed, String reply) {
        return new EvalResult(s.id(), s.tags(), v, reason, failed, reply, s.review(),
                s.expectedBehavior(), s.rubric());
    }
}
