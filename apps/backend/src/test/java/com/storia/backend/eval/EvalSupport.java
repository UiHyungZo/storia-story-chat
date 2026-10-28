package com.storia.backend.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** 시나리오 로딩과 JSON/Markdown 리포트 생성. */
final class EvalSupport {

    static final String SCENARIO_RESOURCE = "/eval/scenarios.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EvalSupport() {
    }

    static EvalScenarioFile loadScenarios() throws IOException {
        try (InputStream in = EvalSupport.class.getResourceAsStream(SCENARIO_RESOURCE)) {
            if (in == null) {
                throw new IOException("시나리오 파일 없음: " + SCENARIO_RESOURCE);
            }
            return MAPPER.readValue(in, EvalScenarioFile.class);
        }
    }

    /** 리포트를 outDir에 {@code <runId>.json}, {@code <runId>.md}로 쓰고 runId를 반환한다. */
    static String writeReport(Path outDir, EvalScenarioFile file, ReplyGenerator generator,
                              String method, List<EvalResult> results) throws IOException {
        String runId = "eval-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Long> totals = new LinkedHashMap<>();
        for (Verdict v : Verdict.values()) {
            totals.put(v.name().toLowerCase(), results.stream().filter(r -> r.verdict() == v).count());
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("run_id", runId);
        report.put("started_at", Instant.now().toString());
        report.put("model", generator.modelName());
        report.put("generation_settings", generator.generationSettings());
        report.put("prompt_version", file.promptVersion());
        report.put("eval_method", method);
        report.put("total", results.size());
        report.put("totals", totals);
        report.put("results", results);

        Files.createDirectories(outDir);
        Files.writeString(outDir.resolve(runId + ".json"),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardCharsets.UTF_8);
        Files.writeString(outDir.resolve(runId + ".md"), markdown(report, totals, results), StandardCharsets.UTF_8);
        return runId;
    }

    private static String markdown(Map<String, Object> report, Map<String, Long> totals, List<EvalResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Evaluation 리포트 ").append(report.get("run_id")).append("\n\n");
        sb.append("- 모델: ").append(report.get("model")).append('\n');
        sb.append("- 생성 설정: ").append(report.get("generation_settings")).append('\n');
        sb.append("- 프롬프트 버전: ").append(report.get("prompt_version")).append('\n');
        sb.append("- 평가 방법: ").append(report.get("eval_method")).append('\n');
        sb.append("- 시작 시각: ").append(report.get("started_at")).append("\n\n");
        sb.append("합성 시나리오 결과이며 실제 사용자 환경의 성능을 의미하지 않는다. ")
                .append("`undetermined`는 성공이 아니다 — 사람의 rubric 검토가 필요하다.\n\n");
        sb.append("| 전체 | pass | fail | undetermined |\n| --- | --- | --- | --- |\n");
        sb.append("| ").append(results.size()).append(" | ").append(totals.get("pass")).append(" | ")
                .append(totals.get("fail")).append(" | ").append(totals.get("undetermined")).append(" |\n\n");

        sb.append("## 유형(tag)별 결과\n\n| tag | pass | fail | undetermined |\n| --- | --- | --- | --- |\n");
        Map<String, List<EvalResult>> byTag = new java.util.TreeMap<>();
        for (EvalResult r : results) {
            for (String tag : r.tags()) {
                byTag.computeIfAbsent(tag, k -> new java.util.ArrayList<>()).add(r);
            }
        }
        byTag.forEach((tag, list) -> sb.append("| ").append(tag).append(" | ")
                .append(count(list, Verdict.PASS)).append(" | ").append(count(list, Verdict.FAIL)).append(" | ")
                .append(count(list, Verdict.UNDETERMINED)).append(" |\n"));

        sb.append("\n## 시나리오별 결과\n\n| id | 판정 | 사유 | 실패한 검증 |\n| --- | --- | --- | --- |\n");
        for (EvalResult r : results) {
            sb.append("| ").append(r.id()).append(" | ").append(r.verdict().name().toLowerCase()).append(" | ")
                    .append(r.reason()).append(" | ")
                    .append(r.failedChecks().stream().map(c -> c.replace("|", "\\|")).collect(Collectors.joining("<br>")))
                    .append(" |\n");
        }
        return sb.toString();
    }

    private static long count(List<EvalResult> list, Verdict v) {
        return list.stream().filter(r -> r.verdict() == v).count();
    }
}
