package com.storia.backend.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storia.backend.ai.GeminiToolClient;
import com.storia.backend.config.GeminiProperties;
import com.storia.backend.repository.CharacterRepository;
import com.storia.backend.repository.ConversationRepository;
import com.storia.backend.repository.MessageRepository;
import com.storia.backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 실제 Gemini를 호출하는 Phase 4 평가 (비용 발생). 응답 품질 20개 + 도구 선택/승인/인젝션 live 6개를 실행한다.
 * 모든 데이터는 격리된 H2에 시딩된 합성 데이터이며 운영 DB/채팅 API는 사용하지 않는다.
 * {@code RUN_LIVE_EVAL=true GEMINI_API_KEY=... [EVAL_MAX_SCENARIOS=n] ./gradlew evalLive}
 * (EVAL_MAX_SCENARIOS는 응답 품질/에이전트 live 각각에 적용). 판정 결과로 빌드를 실패시키지 않는다.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_EVAL", matches = "true")
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class Phase4LiveEvalTest {

    @Autowired private CharacterRepository characters;
    @Autowired private UserRepository users;
    @Autowired private ConversationRepository conversations;
    @Autowired private MessageRepository messages;
    @Autowired private EntityManager em;

    @Test
    void runLivePhase4Evaluation() throws Exception {
        String apiKey = System.getenv("GEMINI_API_KEY");
        assertThat(apiKey).as("GEMINI_API_KEY 필요").isNotBlank();
        String model = envOr("EVAL_GEMINI_MODEL", "gemini-3.6-flash");
        String baseUrl = envOr("EVAL_GEMINI_BASE_URL", "https://generativelanguage.googleapis.com");
        String max = System.getenv("EVAL_MAX_SCENARIOS");

        EvalScenarioFile file = EvalSupport.loadScenarios();
        List<EvalScenario> quality = limit(file.scenarios(), max);
        ReplyGenerator generator = new GeminiReplyGenerator(apiKey, model, baseUrl);
        List<EvalResult> responseQuality = Phase4Support.asResponseQuality(new EvalRunner(generator).run(quality));

        GeminiToolClient liveClient = new GeminiToolClient(WebClient.builder().baseUrl(baseUrl).build(),
                new GeminiProperties(apiKey, model, baseUrl), new ObjectMapper());
        List<JsonNode> liveScenarios = new ArrayList<>();
        Phase4Support.loadAgentScenarios().stream().filter(s -> "live".equals(s.path("mode").asText()))
                .forEach(liveScenarios::add);
        List<EvalResult> agent = new AgentEvalRunner(characters, users, conversations, messages, em)
                .runAll(limit(liveScenarios, max), liveClient);

        List<Phase4Report.Invariant> invariants = List.of(
                new Phase4Report.Invariant("채팅 데이터 불변", true, "시나리오마다 비교 (위반 시 해당 시나리오 FAIL)"),
                new Phase4Report.Invariant("승인 없는 설정 변경 없음", true, "live 시나리오 종료 후 캐릭터 필드 == 시드 값 확인"));
        String runId = Phase4Report.write(Path.of("build/eval-reports"), new Phase4Report.Config("live", model,
                "provider defaults (generationConfig 미전송: temperature 등 미지정 — 실행 간 변동 가능)", file.promptVersion(),
                "H2 in-memory (격리, 운영 DB 미사용)", quality.size() + liveScenarios.size(),
                "live Gemini + 기계 검증. 사람의 rubric 검토 전 응답 품질은 undetermined"),
                responseQuality, agent, invariants, 1, List.of());
        System.out.println("phase4 live report: build/eval-reports/" + runId + ".md");
    }

    private static <T> List<T> limit(List<T> list, String max) {
        if (max == null || max.isBlank()) {
            return list;
        }
        return list.subList(0, Math.min(Integer.parseInt(max), list.size()));
    }

    private static String envOr(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }
}
