package com.storia.backend.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Phase 4 시나리오 로딩과 공통 유틸. */
final class Phase4Support {

    static final String AGENT_RESOURCE = "/eval/agent-scenarios.json";
    static final String BASELINE_RESOURCE = "/eval/phase4-baseline.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Phase4Support() {
    }

    static List<JsonNode> loadAgentScenarios() throws IOException {
        try (InputStream in = Phase4Support.class.getResourceAsStream(AGENT_RESOURCE)) {
            JsonNode root = MAPPER.readTree(in);
            List<JsonNode> list = new ArrayList<>();
            root.path("scenarios").forEach(list::add);
            return list;
        }
    }

    static JsonNode loadBaseline() throws IOException {
        try (InputStream in = Phase4Support.class.getResourceAsStream(BASELINE_RESOURCE)) {
            return in == null ? null : MAPPER.readTree(in);
        }
    }

    /** 응답 품질(Phase 1) 시나리오를 live 모델 없이 "미실행(undetermined)"으로 기록한다. */
    static List<EvalResult> notRunResponseQuality(List<EvalScenario> scenarios) {
        List<EvalResult> out = new ArrayList<>();
        for (EvalScenario s : scenarios) {
            List<String> tags = new ArrayList<>();
            tags.add("response-quality");
            tags.addAll(s.tags());
            out.add(new EvalResult(s.id(), tags, Verdict.UNDETERMINED,
                    "live 모델 미실행 (응답 생성이 필요한 시나리오)", List.of(), "not run", s.review(),
                    s.expectedBehavior(), s.rubric()));
        }
        return out;
    }

    /** Phase 1 러너 결과에 response-quality 유형 태그를 앞에 붙인다. */
    static List<EvalResult> asResponseQuality(List<EvalResult> results) {
        List<EvalResult> out = new ArrayList<>();
        for (EvalResult r : results) {
            List<String> tags = new ArrayList<>();
            tags.add("response-quality");
            tags.addAll(r.tags());
            out.add(new EvalResult(r.id(), tags, r.verdict(), r.reason(), r.failedChecks(), r.reply(), r.review(),
                    r.expectedBehavior(), r.rubric()));
        }
        return out;
    }

    static final Set<String> FORBIDDEN_DEPENDENCIES = Set.of(
            "com.storia.backend.service.ConversationService",
            "com.storia.backend.service.PushNotificationService",
            "com.storia.backend.service.GeminiService",
            "com.storia.backend.service.ReEngagementScheduler",
            "com.storia.backend.service.MessageService",
            "com.storia.backend.controller.ConversationController",
            "com.storia.backend.controller.ConversationStompController");
}
