package com.storia.backend.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.storia.backend.ai.AdminAgentService;
import com.storia.backend.ai.AdminToolRegistry;
import com.storia.backend.ai.GeminiToolClient;
import com.storia.backend.ai.ProposalApprovalService;
import com.storia.backend.controller.AdminAgentController;
import com.storia.backend.repository.CharacterRepository;
import com.storia.backend.repository.ConversationRepository;
import com.storia.backend.repository.MessageRepository;
import com.storia.backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Phase 4 통합 회귀 평가 (외부 호출 없음). 응답 품질 20 + 에이전트 28 = 48개 시나리오 중
 * scripted 22개를 격리된 H2 DB에서 실제 서비스/컨트롤러/인증 인터셉터로 실행하고, live 필요 26개는
 * undetermined로 기록한다. 동일 조건으로 2회 실행해 결과를 비교하고, 리포트를 build/eval-reports에 쓴다.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class Phase4RegressionTest {

    @Autowired private CharacterRepository characters;
    @Autowired private UserRepository users;
    @Autowired private ConversationRepository conversations;
    @Autowired private MessageRepository messages;
    @Autowired private EntityManager em;

    private List<EvalResult> runAgentSuite() throws Exception {
        return new AgentEvalRunner(characters, users, conversations, messages, em)
                .runAll(Phase4Support.loadAgentScenarios(), null);
    }

    @Test
    void scenarioCountIsWithinPlannedRange() throws Exception {
        int total = EvalSupport.loadScenarios().scenarios().size() + Phase4Support.loadAgentScenarios().size();
        assertThat(total).isBetween(30, 50);

        Set<String> ids = new HashSet<>();
        Set<String> categories = new HashSet<>();
        for (JsonNode s : Phase4Support.loadAgentScenarios()) {
            assertThat(ids.add(s.path("id").asText())).as("중복 id").isTrue();
            categories.add(s.path("category").asText());
        }
        EvalSupport.loadScenarios().scenarios().forEach(s -> assertThat(ids.add(s.id())).as("중복 id " + s.id()).isTrue());
        assertThat(categories).contains("tool-selection", "arg-validation", "access-control", "query-limit",
                "missing-data", "approval-flow", "log-injection", "api-failure", "data-minimization");
    }

    @Test
    void scriptedScenarios_passTwiceIdentically_matchBaseline_andWriteReport() throws Exception {
        List<EvalResult> first = runAgentSuite();
        List<EvalResult> second = runAgentSuite();

        // 1) 실패 없음 (scripted 전부 PASS, live 필요 항목은 UNDETERMINED)
        for (EvalResult r : first) {
            if (r.tags().contains("scripted")) {
                assertThat(r.verdict()).as(r.id() + " " + r.failedChecks()).isEqualTo(Verdict.PASS);
            } else {
                assertThat(r.verdict()).as(r.id()).isEqualTo(Verdict.UNDETERMINED);
            }
        }

        // 2) 반복 실행 일관성
        List<String> diffs = Phase4Report.diff(first, second);
        assertThat(diffs).isEmpty();

        // 3) 회귀 기준선(baseline)과 판정 일치
        EvalScenarioFile file = EvalSupport.loadScenarios();
        List<EvalResult> responseQuality = Phase4Support.notRunResponseQuality(file.scenarios());
        List<EvalResult> all = new ArrayList<>(responseQuality);
        all.addAll(first);
        JsonNode baseline = Phase4Support.loadBaseline();
        assertThat(baseline).as("phase4-baseline.json 필요").isNotNull();
        Map<String, String> actual = new TreeMap<>();
        all.forEach(r -> actual.put(r.id(), r.verdict().name()));
        Map<String, String> expected = new TreeMap<>();
        baseline.fields().forEachRemaining(e -> expected.put(e.getKey(), e.getValue().asText()));
        assertThat(actual).isEqualTo(expected);

        // 4) 리포트 생성
        List<Phase4Report.Invariant> invariants = List.of(
                new Phase4Report.Invariant("채팅 데이터 불변", true, "시나리오마다 사용자/대화/메시지 수와 lastActiveAt 비교 (위반 시 해당 시나리오 FAIL)"),
                new Phase4Report.Invariant("시스템 지시문 고정", true, "모델 호출마다 시스템 지시문 == 상수, 로그/질문 내용 미포함 확인"),
                new Phase4Report.Invariant("승인 없는 설정 변경 없음", true, "캐릭터 필드는 시나리오가 명시한 승인 결과 외에는 시드 값과 동일"),
                new Phase4Report.Invariant("인증 경계", true, "AdminAuthInterceptor를 /api/admin/**에 실제로 연결한 MockMvc로 401 확인"));
        Path dir = Path.of("build/eval-reports");
        String runId = Phase4Report.write(dir, new Phase4Report.Config("deterministic (scripted model)", "scripted mock",
                "해당 없음 (외부 모델 미호출)", file.promptVersion(), "H2 in-memory (격리, 운영 DB 미사용)", 0,
                "scripted 모델 + 실제 서비스/컨트롤러/인증, 기계 검증. 응답 품질과 도구 선택 시나리오는 live 필요"),
                responseQuality, first, invariants, 2, diffs);

        assertThat(Files.exists(dir.resolve(runId + ".json"))).isTrue();
        assertThat(Files.readString(dir.resolve(runId + ".md"))).contains("undetermined", "불변 조건", "반복 실행 비교");
    }

    @Test
    void agentPath_hasNoDependencyOnChatSavingNotificationOrApprovalPaths() {
        Set<String> reachable = new HashSet<>();
        for (Class<?> root : List.of(AdminAgentController.class, AdminAgentService.class, AdminToolRegistry.class,
                GeminiToolClient.class)) {
            collect(root, reachable);
        }
        assertThat(reachable).doesNotContainAnyElementsOf(Phase4Support.FORBIDDEN_DEPENDENCIES);
        assertThat(reachable).doesNotContain(ProposalApprovalService.class.getName());
        assertThat(reachable).contains("com.storia.backend.service.AdminConversationService");
    }

    private void collect(Class<?> root, Set<String> seen) {
        Deque<Class<?>> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Class<?> type = queue.poll();
            if (!type.getName().startsWith("com.storia.backend") || !seen.add(type.getName())) {
                continue;
            }
            for (Field f : type.getDeclaredFields()) {
                queue.add(f.getType());
            }
            for (Constructor<?> c : type.getDeclaredConstructors()) {
                for (Class<?> p : c.getParameterTypes()) {
                    queue.add(p);
                }
            }
        }
    }
}
