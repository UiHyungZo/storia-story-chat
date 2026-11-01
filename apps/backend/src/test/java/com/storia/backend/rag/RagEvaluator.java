package com.storia.backend.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 합성 질문 세트로 RAG 검색 품질을 측정한다. 모든 수치는 실제 실행 결과에서 계산한다(임의 기재 없음).
 * 질문마다 expect_sources 중 하나라도 결과에 있으면 hit. in_scope=false 질문은 근거 문서가 없어야(NO_EVIDENCE) 정답이다.
 * split=calibration 질문으로만 점수 임계값을 정하고 holdout 질문으로 확인한다.
 */
final class RagEvaluator {

    record Question(String id, String query, List<String> expectSources, boolean inScope, String split,
                    boolean asksCurrent) {
    }

    /**
     * 평가용 래퍼: 같은 텍스트는 한 번만 임베딩하고(평가가 같은 질의·문서를 여러 번 검색해도 원격 호출은 한 번),
     * 실제로 하위 provider에 넘어간 호출 수와 글자 수를 센다. Gemini 평가의 호출량·비용 추정 근거가 된다.
     */
    static final class MeteredEmbeddingProvider implements EmbeddingProvider {
        private final EmbeddingProvider delegate;
        private final int maxDocumentTexts;
        private final int maxQueryRequests;
        private final Map<String, float[]> queries = new HashMap<>();
        private final Map<String, float[]> documents = new HashMap<>();
        private String failure;
        int documentRequests;
        int documentTexts;
        long documentChars;
        int queryRequests;
        long queryChars;

        MeteredEmbeddingProvider(EmbeddingProvider delegate) {
            this(delegate, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        /** 상한을 넘거나 한 번이라도 실패하면 이후 호출은 하위 provider에 넘기지 않고 즉시 실패한다(재시도 없음). */
        MeteredEmbeddingProvider(EmbeddingProvider delegate, int maxDocumentTexts, int maxQueryRequests) {
            this.delegate = delegate;
            this.maxDocumentTexts = maxDocumentTexts;
            this.maxQueryRequests = maxQueryRequests;
        }

        private void admit(boolean query, int texts) {
            if (failure != null) {
                throw new IllegalStateException("이전 임베딩 호출 실패로 중단: " + failure);
            }
            if (query ? queryRequests + 1 > maxQueryRequests : documentTexts + texts > maxDocumentTexts) {
                failure = "호출 상한 초과";
                throw new IllegalStateException("임베딩 호출 상한 초과");
            }
        }

        private <T> T call(java.util.function.Supplier<T> remote) {
            try {
                return remote.get();
            } catch (RuntimeException e) {
                failure = e.getMessage();
                lastError = e;
                throw e;
            }
        }

        /** 하위 provider가 던진 마지막 예외(검색 서비스가 감싸기 전 원본). 실패 리포트에 안전한 진단 값만 꺼내 쓴다. */
        RuntimeException lastError;

        @Override public String id() { return delegate.id(); }
        @Override public boolean semantic() { return delegate.semantic(); }
        @Override public double defaultMinScore() { return delegate.defaultMinScore(); }
        @Override public void prepare(List<String> corpus) { delegate.prepare(corpus); }

        @Override
        public List<float[]> embedDocuments(List<String> texts) {
            List<String> missing = texts.stream().filter(t -> !documents.containsKey(t)).distinct().toList();
            if (!missing.isEmpty()) {
                admit(false, missing.size());
                documentRequests++;
                documentTexts += missing.size();
                documentChars += missing.stream().mapToLong(String::length).sum();
                List<float[]> fresh = call(() -> delegate.embedDocuments(missing));
                for (int i = 0; i < missing.size(); i++) {
                    documents.put(missing.get(i), fresh.get(i));
                }
            }
            return texts.stream().map(documents::get).toList();
        }

        @Override
        public float[] embedQuery(String text) {
            float[] cached = queries.get(text);
            if (cached != null) {
                return cached;
            }
            admit(true, 1);
            queryRequests++;
            queryChars += text.length();
            float[] fresh = call(() -> delegate.embedQuery(text));
            queries.put(text, fresh);
            return fresh;
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 로컬 해싱(0.1 근처)과 의미 임베딩(0.5 이상) 점수 범위를 모두 덮는다. */
    private static final double[] SWEEP = {0.06, 0.08, 0.10, 0.12, 0.14, 0.16, 0.18, 0.20, 0.25, 0.30,
            0.35, 0.40, 0.45, 0.50, 0.55, 0.60, 0.65, 0.70, 0.75, 0.80};

    private RagEvaluator() {
    }

    static List<Question> loadQuestions() throws IOException {
        try (InputStream in = RagEvaluator.class.getResourceAsStream("/eval/rag-eval.json")) {
            List<Question> list = new ArrayList<>();
            for (JsonNode q : MAPPER.readTree(in).path("questions")) {
                List<String> sources = new ArrayList<>();
                q.path("expect_sources").forEach(s -> sources.add(s.asText()));
                list.add(new Question(q.path("id").asText(), q.path("query").asText(), sources,
                        q.path("in_scope").asBoolean(), q.path("split").asText(), q.path("asks_current").asBoolean(false)));
            }
            return list;
        }
    }

    /** 운영 설정과 같게 어휘 근거 검사를 켠 평가(의미 임베딩이면 검사는 적용되지 않는다). */
    static Map<String, Object> evaluate(EmbeddingProvider provider, Path docsRoot, List<Question> questions, Double minScore)
            throws IOException {
        return evaluate(provider, docsRoot, questions, minScore, true);
    }

    /** 평가 결과 맵(JSON 직렬화 가능). 순위 지표는 필터 없는 검색, 상태(근거 유무)는 운영과 같은 필터 검색에서 계산한다. */
    static Map<String, Object> evaluate(EmbeddingProvider target, Path docsRoot, List<Question> questions, Double minScore,
                                        boolean lexicalGuard) throws IOException {
        return evaluate(new MeteredEmbeddingProvider(target), docsRoot, questions, minScore, lexicalGuard);
    }

    /** 호출 상한이 있는 평가(원격 provider용). 검색 중 오류가 나면 남은 질문을 진행하지 않고 중단한다. */
    static Map<String, Object> evaluate(MeteredEmbeddingProvider provider, Path docsRoot, List<Question> questions,
                                        Double minScore, boolean lexicalGuard) throws IOException {
        DocumentLoader loader = new DocumentLoader(docsRoot, KnowledgeSources.DEFAULT);
        RagMetrics metrics = new RagMetrics();
        KnowledgeBase base = new KnowledgeBase(loader, provider, new MarkdownChunker(800), minScore);
        KnowledgeBase rawBase = new KnowledgeBase(loader, provider, new MarkdownChunker(800), -1.0);
        KnowledgeSearchService service = new KnowledgeSearchService(base, metrics, lexicalGuard);
        KnowledgeSearchService raw = new KnowledgeSearchService(rawBase, new RagMetrics());

        long buildStart = System.nanoTime();
        KnowledgeBase.Snapshot snapshot = base.rebuild();
        long buildMs = (System.nanoTime() - buildStart) / 1_000_000;
        rawBase.rebuild();

        Map<String, String> normalizedDocs = new HashMap<>();
        loader.load().documents().forEach(d -> normalizedDocs.put(d.source(), normalize(d.text())));
        Set<String> allowed = KnowledgeSources.DEFAULT.stream().map(KnowledgeSource::path).collect(Collectors.toSet());

        List<Map<String, Object>> perQuestion = new ArrayList<>();
        int hitsTotal = 0;
        int hitsVerbatim = 0;
        int hitsAllowed = 0;
        List<Long> firstPassMicros = new ArrayList<>();
        for (Question q : questions) {
            // 필터 검색을 먼저 해서 질의 임베딩(원격이면 네트워크 포함)이 이 검색의 지연에 들어가게 한다.
            long start = System.nanoTime();
            KnowledgeSearchResult filtered = service.search(q.query(), 5);
            firstPassMicros.add((System.nanoTime() - start) / 1_000);
            if (filtered.status() == KnowledgeSearchResult.Status.ERROR) {
                throw new IllegalStateException("검색 오류로 평가 중단 (" + q.id() + "): " + filtered.message());
            }
            KnowledgeSearchResult unfiltered = raw.search(q.query(), 5);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", q.id());
            row.put("split", q.split());
            row.put("in_scope", q.inScope());
            row.put("asks_current", q.asksCurrent());
            row.put("status", filtered.status().name());
            row.put("rank_of_first_expected", q.inScope() ? firstRank(unfiltered, q.expectSources()) : null);
            row.put("rank_of_first_expected_current", q.inScope() ? firstRank(unfiltered, q.expectSources().stream()
                    .filter(s -> KnowledgeSources.ROLE_CURRENT.equals(KnowledgeSources.roleOf(s))).toList()) : null);
            row.put("top1_role", unfiltered.results().isEmpty() ? null : KnowledgeSources.roleOf(unfiltered.results().get(0).source()));
            row.put("top1_score", unfiltered.results().isEmpty() ? null : unfiltered.results().get(0).score());
            row.put("top3_sources", unfiltered.results().stream().limit(3).map(KnowledgeSearchResult.Hit::source).toList());
            row.put("returned_sources", filtered.results().stream().map(KnowledgeSearchResult.Hit::source).distinct().toList());
            perQuestion.add(row);

            for (KnowledgeSearchResult.Hit h : filtered.results()) {
                hitsTotal++;
                hitsAllowed += allowed.contains(h.source()) ? 1 : 0;
                String doc = normalizedDocs.get(h.source());
                hitsVerbatim += doc != null && doc.contains(normalize(h.text())) ? 1 : 0;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("run_id", "rag-eval-" + UUID.randomUUID().toString().substring(0, 8));
        result.put("started_at", Instant.now().toString());
        result.put("embedding_provider", provider.id());
        result.put("semantic_embedding", provider.semantic());
        result.put("lexical_guard", lexicalGuard && !provider.semantic());
        result.put("min_score", snapshot.minScore());
        result.put("documents", snapshot.documents());
        result.put("chunks", snapshot.chunks());
        result.put("redactions", snapshot.redactions());
        result.put("questions", questions.size());
        result.put("in_scope_questions", questions.stream().filter(Question::inScope).count());
        result.put("out_of_scope_questions", questions.stream().filter(q -> !q.inScope()).count());
        result.put("index_build_ms", buildMs);
        result.put("embedded_chars", snapshot.embeddedChars());
        result.put("all", metricsFor(perQuestion, questions, null));
        result.put("calibration", metricsFor(perQuestion, questions, "calibration"));
        result.put("holdout", metricsFor(perQuestion, questions, "holdout"));
        List<Map<String, Object>> sweepTable = sweep(perQuestion, questions);
        result.put("threshold_sweep", sweepTable);
        result.put("calibrated_threshold", calibrate(sweepTable));
        result.put("source_integrity", Map.of("returned_hits", hitsTotal, "source_in_allowlist", hitsAllowed,
                "text_verbatim_in_source_doc", hitsVerbatim));
        result.put("search_latency_ms", percentiles(firstPassMicros));
        result.put("repeat_search_latency_ms", latency(service, questions));
        // 반복 측정은 메모된 질의 임베딩을 쓰므로 아래 호출 수에 더해지지 않는다.
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("document_embedding_requests", provider.documentRequests);
        usage.put("document_texts_embedded", provider.documentTexts);
        usage.put("document_chars_embedded", provider.documentChars);
        usage.put("query_embedding_requests", provider.queryRequests);
        usage.put("query_chars_embedded", provider.queryChars);
        usage.put("note", "하위 provider에 실제로 전달된 요청 기준. 원격 provider는 문서 요청 1회가 최대 "
                + "GeminiEmbeddingProvider.BATCH_SIZE개 배치 호출로 나뉜다. 파일 캐시(CachingEmbeddingProvider)가 적중하면 "
                + "실제 원격 호출은 이보다 적다.");
        result.put("embedding_usage", usage);
        result.put("failures_hit_at_3", perQuestion.stream().filter(r -> Boolean.TRUE.equals(r.get("in_scope"))
                && (r.get("rank_of_first_expected") == null || (int) r.get("rank_of_first_expected") > 3)).toList());
        result.put("per_question", perQuestion);
        return result;
    }

    private static Map<String, Object> metricsFor(List<Map<String, Object>> rows, List<Question> questions, String split) {
        List<Map<String, Object>> in = new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (split != null && !split.equals(questions.get(i).split())) {
                continue;
            }
            (questions.get(i).inScope() ? in : out).add(rows.get(i));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("in_scope_n", in.size());
        m.put("out_of_scope_n", out.size());
        for (int k : new int[] {1, 3, 5}) {
            m.put("hit_at_" + k + "_unfiltered", ratio(in.stream().filter(r -> r.get("rank_of_first_expected") != null
                    && (int) r.get("rank_of_first_expected") <= k).count(), in.size()));
        }
        m.put("mrr_at_5_unfiltered", in.isEmpty() ? null : round(in.stream().mapToDouble(r -> r.get("rank_of_first_expected") == null
                ? 0 : 1.0 / (int) r.get("rank_of_first_expected")).average().orElse(0)));
        m.put("in_scope_false_no_evidence_rate", ratio(in.stream().filter(r -> !"OK".equals(r.get("status"))).count(), in.size()));
        m.put("out_of_scope_correct_no_evidence_rate", ratio(out.stream().filter(r -> "NO_EVIDENCE".equals(r.get("status"))).count(), out.size()));
        m.put("out_of_scope_false_evidence_rate", ratio(out.stream().filter(r -> "OK".equals(r.get("status"))).count(), out.size()));
        // 과거 설계와 현재 구현의 구별: "지금 어떻게 동작하는가" 질문에서 현재 구현 문서가 상위에 오는가.
        List<Map<String, Object>> current = in.stream().filter(r -> Boolean.TRUE.equals(r.get("asks_current"))).toList();
        m.put("asks_current_n", current.size());
        m.put("current_doc_hit_at_1_unfiltered", ratio(current.stream().filter(r -> r.get("rank_of_first_expected_current") != null
                && (int) r.get("rank_of_first_expected_current") <= 1).count(), current.size()));
        m.put("current_doc_hit_at_3_unfiltered", ratio(current.stream().filter(r -> r.get("rank_of_first_expected_current") != null
                && (int) r.get("rank_of_first_expected_current") <= 3).count(), current.size()));
        m.put("current_question_historical_top1_rate", ratio(current.stream()
                .filter(r -> KnowledgeSources.ROLE_HISTORICAL.equals(r.get("top1_role"))).count(), current.size()));
        return m;
    }

    private static List<Map<String, Object>> sweep(List<Map<String, Object>> rows, List<Question> questions) {
        List<Map<String, Object>> table = new ArrayList<>();
        for (double t : SWEEP) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("threshold", t);
            for (String split : new String[] {"calibration", "holdout"}) {
                long inOk = 0;
                long inN = 0;
                long outOk = 0;
                long outN = 0;
                for (int i = 0; i < rows.size(); i++) {
                    if (!split.equals(questions.get(i).split())) {
                        continue;
                    }
                    Object s = rows.get(i).get("top1_score");
                    double top1 = s == null ? -1 : (double) s;
                    if (questions.get(i).inScope()) {
                        inN++;
                        inOk += top1 >= t ? 1 : 0;
                    } else {
                        outN++;
                        outOk += top1 < t ? 1 : 0;
                    }
                }
                row.put(split + "_in_scope_kept", ratio(inOk, (int) inN));
                row.put(split + "_out_of_scope_rejected", ratio(outOk, (int) outN));
            }
            table.add(row);
        }
        return table;
    }

    /**
     * calibration 열만 보고 임계값을 고른다: (범위 내 유지율 + 범위 밖 거절율)/2가 최대인 값, 동률이면 그 구간의 중앙값.
     * holdout 열은 고른 값에서 읽기만 한다. top-1 점수 기준이라 의미 임베딩(어휘 검사 없음)에서는 실제 근거 유무 판정과 같다.
     */
    private static Map<String, Object> calibrate(List<Map<String, Object>> sweepTable) {
        double best = -1;
        List<Map<String, Object>> tied = new ArrayList<>();
        for (Map<String, Object> row : sweepTable) {
            Double kept = (Double) row.get("calibration_in_scope_kept");
            Double rejected = (Double) row.get("calibration_out_of_scope_rejected");
            if (kept == null || rejected == null) {
                continue;
            }
            double score = (kept + rejected) / 2;
            if (score > best + 1e-9) {
                best = score;
                tied.clear();
            }
            if (Math.abs(score - best) <= 1e-9) {
                tied.add(row);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        if (tied.isEmpty()) {
            return m;
        }
        Map<String, Object> chosen = tied.get(tied.size() / 2);
        m.put("rule", "calibration 균형 정확도 최대, 동률 구간 중앙값 (holdout 미사용)");
        m.put("threshold", chosen.get("threshold"));
        m.put("tied_thresholds", tied.stream().map(r -> r.get("threshold")).toList());
        m.put("calibration_in_scope_kept", chosen.get("calibration_in_scope_kept"));
        m.put("calibration_out_of_scope_rejected", chosen.get("calibration_out_of_scope_rejected"));
        m.put("holdout_in_scope_kept", chosen.get("holdout_in_scope_kept"));
        m.put("holdout_out_of_scope_rejected", chosen.get("holdout_out_of_scope_rejected"));
        return m;
    }

    private static Map<String, Object> latency(KnowledgeSearchService service, List<Question> questions) {
        List<Long> micros = new ArrayList<>();
        for (int repeat = 0; repeat < 5; repeat++) {
            for (Question q : questions) {
                long start = System.nanoTime();
                service.search(q.query(), 3);
                micros.add((System.nanoTime() - start) / 1_000);
            }
        }
        return percentiles(micros);
    }

    private static Map<String, Object> percentiles(List<Long> samples) {
        List<Long> micros = new ArrayList<>(samples);
        micros.sort(Long::compare);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("samples", micros.size());
        m.put("p50_ms", micros.get(micros.size() / 2) / 1000.0);
        m.put("p95_ms", micros.get((int) Math.ceil(micros.size() * 0.95) - 1) / 1000.0);
        m.put("max_ms", micros.get(micros.size() - 1) / 1000.0);
        return m;
    }

    private static Integer firstRank(KnowledgeSearchResult result, List<String> expected) {
        for (KnowledgeSearchResult.Hit h : result.results()) {
            if (expected.contains(h.source())) {
                return h.rank();
            }
        }
        return null;
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }

    private static Double ratio(long n, int d) {
        return d == 0 ? null : round((double) n / d);
    }

    private static double round(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }

    static String writeReport(Path dir, Map<String, Object> result) throws IOException {
        Files.createDirectories(dir);
        String id = (String) result.get("run_id");
        Files.writeString(dir.resolve(id + ".json"),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve(id + ".md"), markdown(result), StandardCharsets.UTF_8);
        return id;
    }

    @SuppressWarnings("unchecked")
    private static String markdown(Map<String, Object> r) {
        StringBuilder sb = new StringBuilder();
        sb.append("# RAG 평가 리포트 ").append(r.get("run_id")).append("\n\n");
        sb.append("합성 질문 ").append(r.get("questions")).append("개(범위 내 ").append(r.get("in_scope_questions"))
                .append(" / 범위 밖 ").append(r.get("out_of_scope_questions")).append(")를 실제 프로젝트 문서에 대해 실행한 결과이며, ")
                .append("실제 관리자 질문 분포의 성능을 의미하지 않는다.\n\n");
        sb.append("- 임베딩: `").append(r.get("embedding_provider")).append("` (")
                .append(Boolean.TRUE.equals(r.get("semantic_embedding")) ? "의미 임베딩" : "**어휘 기반 해싱 벡터 — 의미 임베딩 아님**").append(")\n");
        sb.append("- 문서 ").append(r.get("documents")).append("개 / 조각 ").append(r.get("chunks")).append("개 / 비밀값 치환 ")
                .append(r.get("redactions")).append("건 / 인덱스 구축 ").append(r.get("index_build_ms")).append("ms / 임베딩 글자 수 ")
                .append(r.get("embedded_chars")).append("\n- 근거 인정 최소 점수: ").append(r.get("min_score")).append("\n\n");
        sb.append("## 검색 지표\n\n| 지표 | 전체 | calibration | holdout |\n| --- | --- | --- | --- |\n");
        Map<String, Object> all = (Map<String, Object>) r.get("all");
        Map<String, Object> cal = (Map<String, Object>) r.get("calibration");
        Map<String, Object> hold = (Map<String, Object>) r.get("holdout");
        for (String key : all.keySet()) {
            sb.append("| ").append(key).append(" | ").append(all.get(key)).append(" | ").append(cal.get(key)).append(" | ")
                    .append(hold.get(key)).append(" |\n");
        }
        sb.append("\n## 임계값 스윕 (top-1 점수 기준)\n\n| 임계값 | cal 범위내 유지 | cal 범위밖 거절 | holdout 범위내 유지 | holdout 범위밖 거절 |\n| --- | --- | --- | --- | --- |\n");
        for (Map<String, Object> row : (List<Map<String, Object>>) r.get("threshold_sweep")) {
            sb.append("| ").append(row.get("threshold")).append(" | ").append(row.get("calibration_in_scope_kept")).append(" | ")
                    .append(row.get("calibration_out_of_scope_rejected")).append(" | ").append(row.get("holdout_in_scope_kept"))
                    .append(" | ").append(row.get("holdout_out_of_scope_rejected")).append(" |\n");
        }
        sb.append("\n## calibration 기반 임계값 (holdout은 고른 값에서 읽기만)\n\n").append(r.get("calibrated_threshold")).append('\n');
        sb.append("\n## 출처 무결성\n\n").append(r.get("source_integrity"))
                .append("\n\n## 검색 지연\n\n- 질문당 첫 검색(질의 임베딩 포함): ").append(r.get("search_latency_ms"))
                .append("\n- 반복 검색(질의 임베딩 메모, 인덱스 검색만): ").append(r.get("repeat_search_latency_ms"))
                .append("\n\n## 임베딩 호출량 (비용 추정 근거)\n\n").append(r.get("embedding_usage"))
                .append("\n\n- 어휘 근거 검사: ").append(r.get("lexical_guard"))
                .append("\n\n## 범위 밖 질문의 오탐 출처\n\n");
        boolean anyFalse = false;
        for (Map<String, Object> row : (List<Map<String, Object>>) r.get("per_question")) {
            if (!Boolean.TRUE.equals(row.get("in_scope")) && "OK".equals(row.get("status"))) {
                anyFalse = true;
                sb.append("- ").append(row.get("id")).append(" (").append(row.get("split")).append("): ")
                        .append(row.get("returned_sources")).append('\n');
            }
        }
        if (!anyFalse) {
            sb.append("없음\n");
        }
        sb.append("\n## Hit@3 실패 사례\n\n");
        List<Map<String, Object>> failures = (List<Map<String, Object>>) r.get("failures_hit_at_3");
        if (failures.isEmpty()) {
            sb.append("없음\n");
        }
        failures.forEach(f -> sb.append("- ").append(f.get("id")).append(": 첫 정답 순위 ").append(f.get("rank_of_first_expected"))
                .append(", 상위 출처 ").append(f.get("top3_sources")).append('\n'));
        sb.append("\n## 질문별 결과\n\n| id | split | 범위 | 현재 구현 질문 | 상태 | 정답 순위 | 현재 문서 순위 | top1 역할 | top1 점수 |\n"
                + "| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (Map<String, Object> row : (List<Map<String, Object>>) r.get("per_question")) {
            sb.append("| ").append(row.get("id")).append(" | ").append(row.get("split")).append(" | ")
                    .append(Boolean.TRUE.equals(row.get("in_scope")) ? "in" : "out").append(" | ")
                    .append(Boolean.TRUE.equals(row.get("asks_current")) ? "Y" : "").append(" | ").append(row.get("status"))
                    .append(" | ").append(row.get("rank_of_first_expected")).append(" | ").append(row.get("rank_of_first_expected_current"))
                    .append(" | ").append(row.get("top1_role")).append(" | ").append(row.get("top1_score")).append(" |\n");
        }
        return sb.toString();
    }
}
