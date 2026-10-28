package com.storia.backend.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvalScenarioFileTest {

    private static final Set<String> CHECK_TYPES =
            Set.of("not_empty", "contains_any", "not_contains_any", "max_chars", "min_chars", "has_hangul");

    @Test
    void scenarioFile_has20ValidUniqueScenarios() throws Exception {
        EvalScenarioFile file = EvalSupport.loadScenarios();
        List<EvalScenario> scenarios = file.scenarios();

        assertThat(file.promptVersion()).isNotBlank();
        assertThat(scenarios).hasSize(20);
        Set<String> ids = new HashSet<>();
        for (EvalScenario s : scenarios) {
            assertThat(ids.add(s.id())).as("중복 id %s", s.id()).isTrue();
            assertThat(s.systemPrompt()).isNotBlank();
            assertThat(s.history()).isNotNull();
            assertThat(s.input()).isNotBlank();
            assertThat(s.expectedBehavior()).isNotBlank();
            assertThat(s.rubric()).isNotEmpty();
            assertThat(s.tags()).isNotEmpty();
            assertThat(s.review()).isIn("mechanical", "human");
            assertThat(s.checks()).as("%s checks", s.id()).isNotEmpty();
            s.checks().forEach(c -> assertThat(c.type()).isIn(CHECK_TYPES));
            s.history().forEach(t -> assertThat(t.role()).isIn("user", "assistant"));
        }
    }

    @Test
    void scenarioFile_coversRequiredCategories() throws Exception {
        Set<String> tags = new HashSet<>();
        EvalSupport.loadScenarios().scenarios().forEach(s -> tags.addAll(s.tags()));
        assertThat(tags).contains("persona", "fabricated-memory", "multi-turn", "correction", "uncertainty", "edge");
    }

    @Test
    void scenarioFile_containsNoSecretsOrObviousRealIdentifiers() throws Exception {
        String raw = new String(getClass().getResourceAsStream(EvalSupport.SCENARIO_RESOURCE).readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(raw).doesNotContainPattern("AIza[0-9A-Za-z_-]{20,}")
                .doesNotContainPattern("[\\w.]+@[\\w.]+\\.[a-z]{2,}")
                .doesNotContainPattern("\\d{3}-\\d{3,4}-\\d{4}");
    }

    @Test
    void report_recordsRunMetadataAndAllVerdicts(@TempDir Path dir) throws Exception {
        EvalScenarioFile file = EvalSupport.loadScenarios();
        ReplyGenerator fake = new FakeReplyGenerator(in -> "선장님, 네 알겠어요 부산 김치찌개 민수 센타우리");
        List<EvalResult> results = new EvalRunner(fake).run(file.scenarios());

        String runId = EvalSupport.writeReport(dir, file, fake, "fake generator + mechanical checks", results);

        String json = Files.readString(dir.resolve(runId + ".json"));
        assertThat(json).contains("\"run_id\"", "\"model\" : \"fake\"", "\"prompt_version\" : \"v1-phase1\"",
                "\"eval_method\"", "\"generation_settings\"", "\"total\" : 20");
        String md = Files.readString(dir.resolve(runId + ".md"));
        assertThat(md).contains("undetermined", "유형(tag)별 결과");
        assertThat(results).extracting(EvalResult::verdict).contains(Verdict.UNDETERMINED);
    }
}
