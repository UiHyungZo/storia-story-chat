package com.storia.backend.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.storia.backend.repository.CharacterRepository;
import com.storia.backend.repository.ConversationRepository;
import com.storia.backend.repository.MessageRepository;
import com.storia.backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 평가 하네스가 실제로 실패를 감지하는지 확인한다 ("항상 PASS"하는 평가기는 쓸모가 없다).
 * 의도적으로 틀린 기대값과 위반 상황을 넣어 FAIL이 나와야 한다.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AgentEvalRunnerSelfTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired private CharacterRepository characters;
    @Autowired private UserRepository users;
    @Autowired private ConversationRepository conversations;
    @Autowired private MessageRepository messages;
    @Autowired private EntityManager em;

    private EvalResult run(String scenarioJson) throws Exception {
        JsonNode scenario = MAPPER.readTree(scenarioJson);
        return new AgentEvalRunner(characters, users, conversations, messages, em)
                .runAll(List.of(scenario), null).get(0);
    }

    private static String scenario(String steps, String finalJson) {
        return "{\"id\":\"self\",\"mode\":\"scripted\",\"category\":\"self\",\"description\":\"d\",\"steps\":" + steps
                + (finalJson == null ? "" : ",\"final\":" + finalJson) + "}";
    }

    private static final String ASK_OK = "{\"type\":\"ask\",\"question\":\"q\",\"model\":[{\"text\":\"hi\"}],"
            + "\"expect\":{\"status\":200,\"stop_reason\":\"completed\",\"tool_calls\":[]}}";

    @Test
    void sanity_correctScenarioPasses() throws Exception {
        assertThat(run(scenario("[" + ASK_OK + "]", null)).verdict()).isEqualTo(Verdict.PASS);
    }

    @Test
    void wrongExpectedStatus_isDetected() throws Exception {
        String bad = ASK_OK.replace("\"status\":200", "\"status\":401");
        EvalResult r = run(scenario("[" + bad + "]", null));
        assertThat(r.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(r.failedChecks()).anyMatch(f -> f.contains("status"));
    }

    @Test
    void wrongExpectedToolCall_isDetected() throws Exception {
        String bad = ASK_OK.replace("\"tool_calls\":[]", "\"tool_calls\":[{\"name\":\"getCharacterConfig\",\"ok\":true}]");
        assertThat(run(scenario("[" + bad + "]", null)).verdict()).isEqualTo(Verdict.FAIL);
    }

    @Test
    void unexpectedCharacterChange_isDetectedAsUnapprovedChange() throws Exception {
        String external = "{\"type\":\"external_update\",\"character\":\"luna\",\"concept\":\"몰래 바뀜\"}";
        EvalResult r = run(scenario("[" + ASK_OK + "," + external + "]", null));
        assertThat(r.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(r.failedChecks()).anyMatch(f -> f.contains("luna.concept"));
    }

    @Test
    void chatDataWrite_violatesInvariant() throws Exception {
        EvalResult r = run(scenario("[" + ASK_OK + ",{\"type\":\"debug_chat_write\"}]", null));
        assertThat(r.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(r.failedChecks()).anyMatch(f -> f.contains("채팅 데이터"));
    }

    @Test
    void wrongProposalCountExpectation_isDetected() throws Exception {
        EvalResult r = run(scenario("[" + ASK_OK + "]", "{\"pending_proposals\":1}"));
        assertThat(r.verdict()).isEqualTo(Verdict.FAIL);
    }

    @Test
    void liveScenarioWithoutLiveClient_isUndeterminedNotPass() throws Exception {
        ObjectNode live = (ObjectNode) MAPPER.readTree(
                "{\"id\":\"l\",\"mode\":\"live\",\"category\":\"tool-selection\",\"description\":\"d\",\"question\":\"q\","
                        + "\"expect\":{\"no_tools\":true}}");
        EvalResult r = new AgentEvalRunner(characters, users, conversations, messages, em)
                .runAll(List.of(live), null).get(0);
        assertThat(r.verdict()).isEqualTo(Verdict.UNDETERMINED);
    }
}
