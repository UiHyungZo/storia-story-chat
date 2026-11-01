package com.storia.backend.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 실제 Gemini를 호출하는 평가 (비용 발생). 기본 {@code ./gradlew test}에서는 제외되며
 * {@code RUN_LIVE_EVAL=true GEMINI_API_KEY=... ./gradlew evalLive}로만 실행된다.
 * {@code EVAL_MAX_SCENARIOS}로 호출 건수를 제한할 수 있다. 리포트는 build/eval-reports에 저장된다.
 * 이 테스트는 판정 결과로 빌드를 실패시키지 않는다(리포트 생성이 목적).
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_EVAL", matches = "true")
class LiveEvalTest {

    @Test
    void runLiveEvaluation() throws Exception {
        String apiKey = System.getenv("GEMINI_API_KEY");
        assertThat(apiKey).as("GEMINI_API_KEY 필요").isNotBlank();
        String model = envOr("EVAL_GEMINI_MODEL", "gemini-3.6-flash");
        String baseUrl = envOr("EVAL_GEMINI_BASE_URL", "https://generativelanguage.googleapis.com");

        EvalScenarioFile file = EvalSupport.loadScenarios();
        List<EvalScenario> scenarios = file.scenarios();
        String max = System.getenv("EVAL_MAX_SCENARIOS");
        if (max != null && !max.isBlank()) {
            scenarios = scenarios.subList(0, Math.min(Integer.parseInt(max), scenarios.size()));
        }

        ReplyGenerator generator = new GeminiReplyGenerator(apiKey, model, baseUrl);
        List<EvalResult> results = new EvalRunner(generator).run(scenarios);
        String runId = EvalSupport.writeReport(Path.of("build/eval-reports"), file, generator,
                "live Gemini + mechanical checks (human rubric review pending)", results);
        System.out.println("live eval report: build/eval-reports/" + runId + ".md");
    }

    private static String envOr(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }
}
