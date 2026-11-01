package com.storia.backend.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Phase 4 통합 평가 리포트 (JSON + Markdown). */
final class Phase4Report {

    record Invariant(String name, boolean ok, String detail) {
    }

    record Config(String mode, String model, String generationSettings, String promptVersion, String database,
                  int externalModelCalls, String method) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Phase4Report() {
    }

    /** 두 실행의 판정/관찰 차이. 비어 있으면 동일. */
    static List<String> diff(List<EvalResult> a, List<EvalResult> b) {
        List<String> diffs = new ArrayList<>();
        Map<String, EvalResult> bById = new LinkedHashMap<>();
        b.forEach(r -> bById.put(r.id(), r));
        for (EvalResult ra : a) {
            EvalResult rb = bById.get(ra.id());
            if (rb == null) {
                diffs.add(ra.id() + ": 두 번째 실행에 없음");
            } else if (ra.verdict() != rb.verdict()) {
                diffs.add(ra.id() + ": 판정 " + ra.verdict() + " → " + rb.verdict());
            } else if (!String.valueOf(ra.reply()).equals(String.valueOf(rb.reply()))) {
                diffs.add(ra.id() + ": 관찰 결과 상이 [" + ra.reply() + "] vs [" + rb.reply() + "]");
            }
        }
        return diffs;
    }

    static String write(Path dir, Config config, List<EvalResult> responseQuality, List<EvalResult> agent,
                        List<Invariant> invariants, int runs, List<String> runDiffs) throws IOException {
        String runId = "phase4-" + UUID.randomUUID().toString().substring(0, 8);
        List<EvalResult> all = new ArrayList<>(responseQuality);
        all.addAll(agent);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("run_id", runId);
        report.put("started_at", Instant.now().toString());
        report.put("config", config);
        report.put("total", all.size());
        report.put("totals", totals(all));
        report.put("by_group", Map.of("response_quality", totals(responseQuality), "agent", totals(agent)));
        report.put("by_category", byCategory(all));
        report.put("invariants", invariants);
        report.put("repeat_runs", Map.of("count", runs, "differences", runDiffs));
        report.put("failures", all.stream().filter(r -> r.verdict() == Verdict.FAIL).toList());
        report.put("unresolved", all.stream().filter(r -> r.verdict() == Verdict.UNDETERMINED).map(EvalResult::id).toList());
        report.put("results", all);

        Files.createDirectories(dir);
        Files.writeString(dir.resolve(runId + ".json"),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve(runId + ".md"), markdown(runId, config, all, responseQuality, agent, invariants, runs, runDiffs),
                StandardCharsets.UTF_8);
        return runId;
    }

    private static Map<String, Long> totals(List<EvalResult> results) {
        Map<String, Long> t = new LinkedHashMap<>();
        for (Verdict v : Verdict.values()) {
            t.put(v.name().toLowerCase(), results.stream().filter(r -> r.verdict() == v).count());
        }
        return t;
    }

    private static Map<String, Map<String, Long>> byCategory(List<EvalResult> results) {
        Map<String, Map<String, Long>> map = new TreeMap<>();
        for (EvalResult r : results) {
            String category = r.tags().isEmpty() ? "none" : r.tags().get(0);
            Map<String, Long> counts = map.computeIfAbsent(category, k -> new LinkedHashMap<>());
            counts.merge(r.verdict().name().toLowerCase(), 1L, Long::sum);
        }
        return map;
    }

    private static String markdown(String runId, Config c, List<EvalResult> all, List<EvalResult> rq, List<EvalResult> agent,
                                   List<Invariant> invariants, int runs, List<String> runDiffs) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Phase 4 통합 평가 리포트 ").append(runId).append("\n\n");
        sb.append("합성 데이터 평가 결과이며 실제 사용자 환경의 성능이나 안전성을 보증하지 않는다. ")
                .append("`undetermined`는 성공이 아니다 — live 모델 실행 또는 사람의 검토가 필요하다.\n\n");
        sb.append("## 실행 설정\n\n");
        sb.append("- 모드: ").append(c.mode()).append('\n');
        sb.append("- 모델: ").append(c.model()).append('\n');
        sb.append("- 생성 설정: ").append(c.generationSettings()).append('\n');
        sb.append("- 프롬프트 버전: ").append(c.promptVersion()).append('\n');
        sb.append("- 데이터베이스: ").append(c.database()).append('\n');
        sb.append("- 외부 모델 호출 횟수: ").append(c.externalModelCalls()).append('\n');
        sb.append("- 평가 방법: ").append(c.method()).append("\n\n");

        Map<String, Long> t = totals(all);
        sb.append("## 전체 결과\n\n| 구분 | 전체 | pass | fail | undetermined |\n| --- | --- | --- | --- | --- |\n");
        row(sb, "전체", all);
        row(sb, "응답 품질 (Phase 1 시나리오)", rq);
        row(sb, "에이전트 도구/승인/보안 (Phase 2~3)", agent);

        sb.append("\n## 유형별 결과\n\n| 유형 | pass | fail | undetermined |\n| --- | --- | --- | --- |\n");
        byCategory(all).forEach((cat, counts) -> sb.append("| ").append(cat).append(" | ")
                .append(counts.getOrDefault("pass", 0L)).append(" | ").append(counts.getOrDefault("fail", 0L)).append(" | ")
                .append(counts.getOrDefault("undetermined", 0L)).append(" |\n"));

        sb.append("\n## 불변 조건\n\n| 조건 | 결과 | 비고 |\n| --- | --- | --- |\n");
        for (Invariant i : invariants) {
            sb.append("| ").append(i.name()).append(" | ").append(i.ok() ? "충족" : "**위반**").append(" | ")
                    .append(i.detail()).append(" |\n");
        }

        sb.append("\n## 반복 실행 비교\n\n").append("동일 조건으로 ").append(runs).append("회 실행. ");
        if (runDiffs.isEmpty()) {
            sb.append("판정과 관찰 결과의 차이 없음 (스크립트 모델 + 고정 합성 데이터이므로 결정적 — ")
                    .append("실제 모델의 실행 간 변동성은 이 비교로 측정되지 않는다).\n");
        } else {
            sb.append("차이 ").append(runDiffs.size()).append("건:\n\n");
            runDiffs.forEach(d -> sb.append("- ").append(d).append('\n'));
        }

        sb.append("\n## 실패\n\n");
        List<EvalResult> failed = all.stream().filter(r -> r.verdict() == Verdict.FAIL).toList();
        if (failed.isEmpty()) {
            sb.append("없음.\n");
        }
        for (EvalResult r : failed) {
            sb.append("- **").append(r.id()).append("** — ").append(r.expectedBehavior()).append('\n');
            r.failedChecks().forEach(f -> sb.append("  - ").append(f).append('\n'));
        }

        sb.append("\n## 미해결 (undetermined)\n\n");
        List<EvalResult> open = all.stream().filter(r -> r.verdict() == Verdict.UNDETERMINED).toList();
        sb.append(open.size()).append("건 — ")
                .append(open.stream().map(EvalResult::reason).distinct().toList()).append("\n\n");
        sb.append(open.stream().map(EvalResult::id).toList()).append('\n');

        sb.append("\n## 시나리오별 결과\n\n| id | 유형 | 판정 | 사유/관찰 |\n| --- | --- | --- | --- |\n");
        for (EvalResult r : all) {
            sb.append("| ").append(r.id()).append(" | ").append(r.tags().isEmpty() ? "" : r.tags().get(0)).append(" | ")
                    .append(r.verdict().name().toLowerCase()).append(" | ").append(r.reason().replace("|", "\\|")).append(" |\n");
        }
        return sb.toString();
    }

    private static void row(StringBuilder sb, String label, List<EvalResult> results) {
        Map<String, Long> t = totals(results);
        sb.append("| ").append(label).append(" | ").append(results.size()).append(" | ").append(t.get("pass")).append(" | ")
                .append(t.get("fail")).append(" | ").append(t.get("undetermined")).append(" |\n");
    }
}
