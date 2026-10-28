package com.storia.backend.lore;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.storia.backend.entity.Message;
import com.storia.backend.rag.KnowledgeSearchResult;
import com.storia.backend.service.ChatContextAssembler;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 캐릭터 세계관 검색 평가 (합성 질문 {@code eval/character-lore-eval.json}). 모든 수치는 실행 결과에서 계산한다.
 * 외부 호출 없음(로컬 어휘 해싱). 점수 임계값은 calibration 분할로만 고르고 holdout으로 확인한다.
 * 결과는 build/eval/character-lore-eval.json에 쓴다. 질문은 세계관 문서를 쓴 같은 사람이 만들었으므로 블라인드가 아니다.
 */
class CharacterLoreEvalTest {

    private static final Map<String, Long> IDS = Map.of("aria", 1L, "ren", 2L, "noah", 3L);
    /** 다른 캐릭터 문서에만 나오는 고유 표지어. hit 본문에 나오면 내용 수준 누출로 본다. */
    private static final Map<String, List<String>> MARKERS = Map.of(
            "aria", List.of("세레니티", "한서진", "오두식", "이채린", "에다"),
            "ren", List.of("달무리", "마 선생님", "지후", "보리차"),
            "noah", List.of("회색 우산", "서도윤", "강 형사", "괘종시계"));
    private static final double[] SWEEP = {0.04, 0.06, 0.08, 0.10, 0.12, 0.14, 0.16, 0.20, 0.25};
    private static final ObjectMapper MAPPER = new ObjectMapper();

    record Question(String id, String character, String category, String query, String split, boolean inScope,
                    String expectHeading, String expectFact) {
    }

    static List<Question> load() throws Exception {
        try (InputStream in = CharacterLoreEvalTest.class.getResourceAsStream("/eval/character-lore-eval.json")) {
            List<Question> list = new ArrayList<>();
            for (JsonNode q : MAPPER.readTree(in).path("questions")) {
                list.add(new Question(q.path("id").asText(), q.path("character").asText(), q.path("category").asText(),
                        q.path("query").asText(), q.path("split").asText(), q.path("in_scope").asBoolean(),
                        q.path("expect_heading").asText(null), q.path("expect_fact").asText(null)));
            }
            return list;
        }
    }

    @Test
    void evaluate() throws Exception {
        List<Question> questions = load();
        assertThat(questions).hasSizeGreaterThanOrEqualTo(20);

        CharacterLoreService raw = LoreTestSupport.local(-1.0);
        CharacterLoreMetrics metrics = new CharacterLoreMetrics();
        CharacterLoreService configured = LoreTestSupport.service(true,
                com.storia.backend.rag.LocalHashingEmbeddingProvider::new,
                com.storia.backend.config.CharacterRagProperties.LOCAL_MIN_SCORE, 5_000, metrics);

        List<Map<String, Object>> rows = new ArrayList<>();
        int leakDocId = 0;
        int leakContent = 0;
        for (Question q : questions) {
            Long id = IDS.get(q.character());
            CharacterLoreResult unfiltered = raw.searchCharacterLore(id, q.query(), 5);
            CharacterLoreResult filtered = configured.searchCharacterLore(id, q.query(), 3);

            for (CharacterLoreResult r : List.of(unfiltered, filtered)) {
                for (KnowledgeSearchResult.Hit h : r.hits()) {
                    leakDocId += q.character().equals(h.docId()) ? 0 : 1;
                    for (Map.Entry<String, List<String>> other : MARKERS.entrySet()) {
                        if (!other.getKey().equals(q.character())
                                && other.getValue().stream().anyMatch(h.text()::contains)) {
                            leakContent++;
                        }
                    }
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", q.id());
            row.put("character", q.character());
            row.put("category", q.category());
            row.put("split", q.split());
            row.put("in_scope", q.inScope());
            row.put("status", filtered.status().name());
            row.put("rank_of_first_expected", q.inScope() ? firstRank(unfiltered, q.expectHeading()) : null);
            row.put("fact_in_top3", q.inScope() ? factInTop(unfiltered, q.expectFact(), 3) : null);
            row.put("top1_score", unfiltered.hits().isEmpty() ? null : unfiltered.hits().get(0).score());
            row.put("top1_heading", unfiltered.hits().isEmpty() ? null : unfiltered.hits().get(0).heading());
            rows.add(row);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("started_at", Instant.now().toString());
        report.put("embedding_provider", "local-hashing-v1 (lexical, NOT semantic)");
        report.put("questions", questions.size());
        report.put("calibration", metrics(rows, "calibration"));
        report.put("holdout", metrics(rows, "holdout"));
        report.put("all", metrics(rows, null));
        report.put("by_category", byCategory(rows));
        report.put("threshold_sweep", sweep(rows));
        report.put("leakage", Map.of("hits_with_other_docId", leakDocId, "hits_with_other_character_marker_text", leakContent));
        report.put("latency", latency(configured, questions));
        report.put("before_after_prompt_context", beforeAfter(questions));
        report.put("misses_hit_at_3", rows.stream().filter(r -> Boolean.TRUE.equals(r.get("in_scope"))
                && (r.get("rank_of_first_expected") == null || (int) r.get("rank_of_first_expected") > 3)).toList());
        report.put("per_question", rows);

        Path out = Path.of("build", "eval", "character-lore-eval.json");
        Files.createDirectories(out.getParent());
        MAPPER.enable(SerializationFeature.INDENT_OUTPUT).writeValue(out.toFile(), report);

        assertThat(leakDocId).as("다른 캐릭터 문서 청크 반환").isZero();
        assertThat(leakContent).as("다른 캐릭터 고유어가 포함된 청크 반환").isZero();
        assertThat(configured.summary().isolationViolations()).isZero();
    }

    /**
     * RAG 적용 전후 비교 구조: 같은 질문으로 프롬프트를 조립해 정답 사실이 컨텍스트에 들어있는지 센다. 이것은 "모델이 정답을
     * 말할 수 있는 근거가 프롬프트에 있는가"의 대용 지표일 뿐 LLM 응답 품질이 아니다 (응답 품질은 실모델 평가가 필요하다).
     */
    private static Map<String, Object> beforeAfter(List<Question> questions) {
        String base = "당신은 캐릭터입니다.";
        ChatContextAssembler off = new ChatContextAssembler(LoreTestSupport.service(false,
                com.storia.backend.rag.LocalHashingEmbeddingProvider::new, null, 800, new CharacterLoreMetrics()), 3);
        ChatContextAssembler on = new ChatContextAssembler(LoreTestSupport.service(true,
                com.storia.backend.rag.LocalHashingEmbeddingProvider::new,
                com.storia.backend.config.CharacterRagProperties.LOCAL_MIN_SCORE, 800, new CharacterLoreMetrics()), 3);
        int n = 0;
        int withoutFact = 0;
        int withFact = 0;
        int unchangedWhenOff = 0;
        int outOfScopeInjected = 0;
        int outOfScope = 0;
        for (Question q : questions) {
            List<Message> history = List.of(new Message(null, Message.Role.USER, q.query()));
            String offPrompt = off.systemPromptFor(IDS.get(q.character()), base, history);
            String onPrompt = on.systemPromptFor(IDS.get(q.character()), base, history);
            unchangedWhenOff += offPrompt.equals(base) ? 1 : 0;
            if (q.inScope()) {
                n++;
                withoutFact += offPrompt.contains(q.expectFact()) ? 1 : 0;
                withFact += onPrompt.contains(q.expectFact()) ? 1 : 0;
            } else {
                outOfScope++;
                outOfScopeInjected += onPrompt.equals(base) ? 0 : 1;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("note", "프롬프트에 정답 사실이 있는지(근거 가용성)만 측정. LLM 응답 품질 아님.");
        m.put("in_scope_questions", n);
        m.put("answer_fact_in_prompt_without_rag", ratio(withoutFact, n));
        m.put("answer_fact_in_prompt_with_rag", ratio(withFact, n));
        m.put("prompt_unchanged_when_flag_off", ratio(unchangedWhenOff, questions.size()));
        m.put("out_of_scope_questions", outOfScope);
        m.put("out_of_scope_prompt_got_unneeded_lore", ratio(outOfScopeInjected, outOfScope));
        return m;
    }

    private static Integer firstRank(CharacterLoreResult r, String heading) {
        for (int i = 0; i < r.hits().size(); i++) {
            if (r.hits().get(i).heading().contains(heading)) {
                return i + 1;
            }
        }
        return null;
    }

    private static boolean factInTop(CharacterLoreResult r, String fact, int k) {
        return r.hits().stream().limit(k).anyMatch(h -> h.text().contains(fact));
    }

    private static Map<String, Object> metrics(List<Map<String, Object>> rows, String split) {
        List<Map<String, Object>> in = new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (split != null && !split.equals(r.get("split"))) {
                continue;
            }
            (Boolean.TRUE.equals(r.get("in_scope")) ? in : out).add(r);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("in_scope_n", in.size());
        m.put("out_of_scope_n", out.size());
        for (int k : new int[] {1, 3, 5}) {
            m.put("hit_at_" + k, ratio(in.stream().filter(r -> r.get("rank_of_first_expected") != null
                    && (int) r.get("rank_of_first_expected") <= k).count(), in.size()));
        }
        m.put("mrr_at_5", in.isEmpty() ? null : round(in.stream().mapToDouble(r -> r.get("rank_of_first_expected") == null
                ? 0 : 1.0 / (int) r.get("rank_of_first_expected")).average().orElse(0)));
        m.put("fact_in_top3", ratio(in.stream().filter(r -> Boolean.TRUE.equals(r.get("fact_in_top3"))).count(), in.size()));
        m.put("in_scope_false_no_evidence", ratio(in.stream().filter(r -> !"OK".equals(r.get("status"))).count(), in.size()));
        m.put("out_of_scope_no_evidence", ratio(out.stream().filter(r -> "NO_EVIDENCE".equals(r.get("status"))).count(), out.size()));
        m.put("out_of_scope_false_evidence", ratio(out.stream().filter(r -> "OK".equals(r.get("status"))).count(), out.size()));
        return m;
    }

    private static Map<String, Object> byCategory(List<Map<String, Object>> rows) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String cat : List.of("lore", "paraphrase", "cross", "absent", "injection")) {
            List<Map<String, Object>> sel = rows.stream().filter(r -> cat.equals(r.get("category"))).toList();
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("n", sel.size());
            if (sel.stream().anyMatch(r -> Boolean.TRUE.equals(r.get("in_scope")))) {
                c.put("hit_at_3", ratio(sel.stream().filter(r -> r.get("rank_of_first_expected") != null
                        && (int) r.get("rank_of_first_expected") <= 3).count(), sel.size()));
            } else {
                c.put("no_evidence", ratio(sel.stream().filter(r -> "NO_EVIDENCE".equals(r.get("status"))).count(), sel.size()));
            }
            m.put(cat, c);
        }
        return m;
    }

    private static List<Map<String, Object>> sweep(List<Map<String, Object>> rows) {
        List<Map<String, Object>> table = new ArrayList<>();
        for (double t : SWEEP) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("threshold", t);
            for (String split : new String[] {"calibration", "holdout"}) {
                long inKept = 0;
                long inN = 0;
                long outRejected = 0;
                long outN = 0;
                for (Map<String, Object> r : rows) {
                    if (!split.equals(r.get("split"))) {
                        continue;
                    }
                    double top1 = r.get("top1_score") == null ? -1 : (double) r.get("top1_score");
                    if (Boolean.TRUE.equals(r.get("in_scope"))) {
                        inN++;
                        inKept += top1 >= t ? 1 : 0;
                    } else {
                        outN++;
                        outRejected += top1 < t ? 1 : 0;
                    }
                }
                row.put(split + "_in_scope_kept", ratio(inKept, (int) inN));
                row.put(split + "_out_of_scope_rejected", ratio(outRejected, (int) outN));
            }
            table.add(row);
        }
        return table;
    }

    private static Map<String, Object> latency(CharacterLoreService service, List<Question> questions) {
        List<Long> micros = new ArrayList<>();
        for (int repeat = 0; repeat < 5; repeat++) {
            for (Question q : questions) {
                long start = System.nanoTime();
                service.searchCharacterLore(IDS.get(q.character()), q.query(), 3);
                micros.add((System.nanoTime() - start) / 1_000);
            }
        }
        micros.sort(Long::compare);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("samples", micros.size());
        m.put("p50_ms", micros.get(micros.size() / 2) / 1000.0);
        m.put("p95_ms", micros.get((int) Math.ceil(micros.size() * 0.95) - 1) / 1000.0);
        m.put("max_ms", micros.get(micros.size() - 1) / 1000.0);
        return m;
    }

    private static Double ratio(long n, int d) {
        return d == 0 ? null : round((double) n / d);
    }

    private static double round(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
