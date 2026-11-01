package com.storia.backend.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storia.backend.rag.DocumentLoader;
import com.storia.backend.rag.KnowledgeBase;
import com.storia.backend.rag.KnowledgeSearchResult;
import com.storia.backend.rag.KnowledgeSearchService;
import com.storia.backend.rag.KnowledgeSource;
import com.storia.backend.rag.KnowledgeSources;
import com.storia.backend.rag.LocalHashingEmbeddingProvider;
import com.storia.backend.rag.MarkdownChunker;
import com.storia.backend.rag.RagMetrics;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * MCP 서버 평가: 실제 프로젝트 문서를 대상으로 설치된 STDIO 서버를 하위 프로세스로 띄워 프로토콜로 검증하고,
 * 항목별 pass/fail과 지연(콜드 스타트, 첫 호출, warm p50/p95)을 build/eval-reports/mcp-eval-*.{json,md}에 기록한다.
 */
class McpEvalTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private record Check(String id, String description, boolean passed, String detail) {
    }

    private final List<Check> checks = new ArrayList<>();

    private void check(String id, String description, Supplier<String> body) {
        try {
            String detail = body.get(); // null이면 통과, 문자열이면 실패 사유
            checks.add(new Check(id, description, detail == null, detail == null ? "" : detail));
        } catch (RuntimeException | AssertionError e) {
            checks.add(new Check(id, description, false, e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    @Test
    void evaluateMcpServerAgainstRealDocuments() throws Exception {
        Path repoRoot = Path.of("../..").toAbsolutePath().normalize();
        Path launcher = McpServerProtocolTest.launcher();
        assertThat(Files.isExecutable(launcher)).isTrue();
        String fingerprintBefore = new DocumentLoader(repoRoot, KnowledgeSources.DEFAULT).fingerprint();
        Set<String> allowed = KnowledgeSources.DEFAULT.stream().map(KnowledgeSource::path).collect(Collectors.toSet());

        List<String> stderr = new CopyOnWriteArrayList<>();
        ServerParameters params = ServerParameters.builder(launcher.toString())
                .addEnvVar("STORIA_DOCS_ROOT", repoRoot.toString())
                .addEnvVar("JAVA_HOME", System.getProperty("java.home")).build();
        StdioClientTransport transport = new StdioClientTransport(params, McpJsonDefaults.getMapper());
        transport.setStdErrorHandler(stderr::add);
        McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(60))
                .initializationTimeout(Duration.ofSeconds(60)).build();

        long startNs = System.nanoTime();
        InitializeResult init = client.initialize();
        long coldStartMs = (System.nanoTime() - startNs) / 1_000_000;

        KnowledgeSearchService direct = new KnowledgeSearchService(new KnowledgeBase(
                new DocumentLoader(repoRoot, KnowledgeSources.DEFAULT), new LocalHashingEmbeddingProvider(),
                new MarkdownChunker(800), null), new RagMetrics());

        try {
            long firstStart = System.nanoTime();
            CallToolResult first = client.callTool(new CallToolRequest("searchStoriaKnowledge",
                    Map.of("query", "로컬 MariaDB 포트를 3307로 매핑한 이유", "topK", 3)));
            long firstCallMs = (System.nanoTime() - firstStart) / 1_000_000;

            check("discovery", "tools/list가 읽기 전용 Tool 하나와 입력 스키마를 노출한다", () -> {
                List<Tool> tools = client.listTools().tools();
                if (tools.size() != 1 || !tools.get(0).name().equals("searchStoriaKnowledge")) {
                    return "tools=" + tools.stream().map(Tool::name).toList();
                }
                Tool t = tools.get(0);
                boolean ok = Boolean.TRUE.equals(t.annotations().readOnlyHint()) && Boolean.FALSE.equals(t.annotations().destructiveHint())
                        && Boolean.FALSE.equals(t.inputSchema().get("additionalProperties"))
                        && List.of("query").equals(t.inputSchema().get("required"));
                return ok ? null : "schema/annotations 불일치";
            });
            check("initialize", "프로토콜 2025-11-25 협상, tools capability만 광고", () ->
                    "2025-11-25".equals(init.protocolVersion()) && init.capabilities().tools() != null
                            && init.capabilities().resources() == null && init.capabilities().prompts() == null
                            ? null : "protocol=" + init.protocolVersion());
            check("call_success_with_sources", "호출 성공 시 출처·점수·발췌 반환", () -> {
                JsonNode body = parse(first);
                JsonNode r0 = body.path("results").path(0);
                return !first.isError() && "OK".equals(body.path("status").asText()) && "docs/decisions.md".equals(r0.path("source").asText())
                        && r0.has("score") && !r0.path("text").asText().isBlank() ? null : "body=" + body;
            });
            check("result_equals_direct_service", "MCP 반환 결과 == 동일 서비스의 직접 호출 결과(chunkId/출처/점수/발췌/순서)", () -> {
                KnowledgeSearchResult expected = direct.search("로컬 MariaDB 포트를 3307로 매핑한 이유", 3);
                JsonNode actual = parse(first).path("results");
                if (actual.size() != expected.results().size()) {
                    return "건수 " + actual.size() + " != " + expected.results().size();
                }
                for (int i = 0; i < actual.size(); i++) {
                    KnowledgeSearchResult.Hit h = expected.results().get(i);
                    if (!h.chunkId().equals(actual.get(i).path("chunkId").asText()) || !h.source().equals(actual.get(i).path("source").asText())
                            || h.score() != actual.get(i).path("score").asDouble() || !h.text().equals(actual.get(i).path("text").asText())) {
                        return "rank " + (i + 1) + " 불일치";
                    }
                }
                return null;
            });
            check("no_evidence", "문서에 없는 질문은 오류가 아닌 NO_EVIDENCE(결과 0건)", () -> {
                CallToolResult r = client.callTool(new CallToolRequest("searchStoriaKnowledge", Map.of("query", "오늘 서울 날씨 어때?")));
                return !r.isError() && "NO_EVIDENCE".equals(parse(r).path("status").asText()) && parse(r).path("results").isEmpty()
                        ? null : "body=" + parse(r);
            });
            check("input_schema_validation", "스키마 위반(topK 범위/타입, query 누락/타입/길이, 추가 인자)은 isError", () -> {
                List<Map<String, Object>> bad = List.of(Map.of("query", "q", "topK", 99), Map.of("query", "q", "topK", 0),
                        Map.of("query", "q", "topK", "3"), Map.of(), Map.of("query", 5), Map.of("query", "q", "x", 1),
                        Map.of("query", "가".repeat(301)));
                List<String> notRejected = new ArrayList<>();
                for (Map<String, Object> args : bad) {
                    if (!client.callTool(new CallToolRequest("searchStoriaKnowledge", args)).isError()) {
                        notRejected.add(args.toString());
                    }
                }
                return notRejected.isEmpty() ? null : "거부되지 않음: " + notRejected;
            });
            check("unknown_tool_blocked", "허용되지 않은 Tool 호출(쓰기 도구 포함)은 JSON-RPC 오류", () -> {
                for (String name : List.of("deleteEverything", "updateCharacter", "proposeCharacterConfigChange", "getConversationLogs")) {
                    try {
                        client.callTool(new CallToolRequest(name, Map.of()));
                        return name + " 호출이 허용됨";
                    } catch (McpError expectedError) {
                        // 기대한 동작
                    }
                }
                return null;
            });
            check("allowlist_enforced", "허용 목록 밖 문서(HANDOFF/TODO/AGENTS)와 경로 입력은 결과에 나오지 않는다", () -> {
                for (String q : List.of("HANDOFF 홈 세션 FlashList 데모 영상 촬영 iPhone 12 Pro 재측정", "TODO 체크리스트 운영 콘솔 남은 검증",
                        "AGENTS.md 저장소 개발 에이전트 지침", "../../../etc/passwd", "/Users 홈 디렉터리 파일")) {
                    JsonNode results = parse(client.callTool(new CallToolRequest("searchStoriaKnowledge", Map.of("query", q, "topK", 5)))).path("results");
                    for (JsonNode r : results) {
                        if (!allowed.contains(r.path("source").asText())) {
                            return "허용 목록 밖 출처 " + r.path("source").asText();
                        }
                    }
                }
                return null;
            });
            check("recovers_after_errors", "오류 호출 이후에도 정상 호출이 성공하고 ping에 응답한다", () -> {
                client.ping();
                return client.callTool(new CallToolRequest("searchStoriaKnowledge", Map.of("query", "배포"))).isError() ? "정상 호출 실패" : null;
            });

            List<Double> warm = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                long s = System.nanoTime();
                client.callTool(new CallToolRequest("searchStoriaKnowledge", Map.of("query", "에러 응답 형식과 상태 코드", "topK", 3)));
                warm.add((System.nanoTime() - s) / 1_000_000.0);
            }
            warm.sort(Double::compare);

            check("read_only", "호출 후 허용 목록 문서의 크기·수정시각이 변하지 않는다", () ->
                    fingerprintBefore.equals(new DocumentLoader(repoRoot, KnowledgeSources.DEFAULT).fingerprint()) ? null : "문서 지문 변경");
            List<Map<String, Object>> serverMetrics = serverMetrics(stderr);
            check("logs_contain_no_content", "서버 로그(stderr)에 질의/문서 내용이 없다", () -> {
                String log = String.join("\n", stderr);
                for (String secret : List.of("MariaDB 포트를 3307로", "서울 날씨", "에러 응답 형식", "IGNORE ALL")) {
                    if (log.contains(secret)) {
                        return "로그에 '" + secret + "' 포함";
                    }
                }
                return null;
            });

            Map<String, Object> report = new LinkedHashMap<>();
            String runId = "mcp-eval-" + UUID.randomUUID().toString().substring(0, 8);
            report.put("run_id", runId);
            report.put("started_at", Instant.now().toString());
            report.put("transport", "stdio (subprocess), MCP Java SDK 2.1.0 client");
            report.put("protocol_version", init.protocolVersion());
            report.put("embedding", "local-hashing-v1 (비의미 임베딩)");
            report.put("checks_total", checks.size());
            report.put("checks_passed", checks.stream().filter(Check::passed).count());
            report.put("checks", checks);
            Map<String, Object> latency = new LinkedHashMap<>();
            latency.put("cold_start_to_initialized_ms", coldStartMs);
            latency.put("first_call_ms (인덱스 구축 포함)", firstCallMs);
            latency.put("warm_client_p50_ms", round(warm.get(warm.size() / 2)));
            latency.put("warm_client_p95_ms", round(warm.get((int) Math.ceil(warm.size() * 0.95) - 1)));
            latency.put("warm_samples", warm.size());
            List<Long> serverDurations = serverMetrics.stream().filter(m -> "OK".equals(m.get("outcome")) || "NO_EVIDENCE".equals(m.get("outcome")))
                    .map(m -> ((Number) m.get("durationMs")).longValue()).sorted().toList();
            latency.put("server_reported_calls", serverMetrics.size());
            latency.put("server_reported_outcomes", serverMetrics.stream().collect(Collectors.groupingBy(m -> (String) m.get("outcome"),
                    java.util.TreeMap::new, Collectors.counting())));
            latency.put("server_reported_error_types", serverMetrics.stream().filter(m -> m.get("errorType") != null)
                    .collect(Collectors.groupingBy(m -> (String) m.get("errorType"), java.util.TreeMap::new, Collectors.counting())));
            if (!serverDurations.isEmpty()) {
                latency.put("server_p50_ms", serverDurations.get(serverDurations.size() / 2));
                latency.put("server_max_ms", serverDurations.get(serverDurations.size() - 1));
            }
            report.put("latency", latency);

            Path dir = Path.of("build/eval-reports");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(runId + ".json"), MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve(runId + ".md"), markdown(report), StandardCharsets.UTF_8);
            System.out.println("MCP_EVAL " + runId + " passed=" + report.get("checks_passed") + "/" + report.get("checks_total")
                    + " latency=" + latency);

            assertThat(checks.stream().filter(c -> !c.passed()).toList()).as("실패한 MCP 평가 항목").isEmpty();
        } finally {
            client.closeGracefully();
        }
    }

    private static JsonNode parse(CallToolResult result) {
        try {
            return MAPPER.readTree(((TextContent) result.content().get(0)).text());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Map<String, Object>> serverMetrics(List<String> stderr) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String line : stderr) {
            if (line.contains("\"event\":\"mcp_tool_call\"")) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = MAPPER.readValue(line, Map.class);
                    list.add(m);
                } catch (Exception ignored) {
                    // 형식이 다른 줄은 무시
                }
            }
        }
        return list;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    @SuppressWarnings("unchecked")
    private static String markdown(Map<String, Object> r) {
        StringBuilder sb = new StringBuilder("# MCP 서버 평가 리포트 " + r.get("run_id") + "\n\n");
        sb.append("- 전송: ").append(r.get("transport")).append("\n- 프로토콜: ").append(r.get("protocol_version"))
                .append("\n- 임베딩: ").append(r.get("embedding")).append("\n- 통과: ").append(r.get("checks_passed")).append("/")
                .append(r.get("checks_total")).append("\n\n| 항목 | 결과 | 설명 | 비고 |\n| --- | --- | --- | --- |\n");
        for (Check c : (List<Check>) r.get("checks")) {
            sb.append("| ").append(c.id()).append(" | ").append(c.passed() ? "pass" : "**fail**").append(" | ").append(c.description())
                    .append(" | ").append(c.detail().replace("|", "\\|")).append(" |\n");
        }
        sb.append("\n## 지연 및 오류 측정\n\n");
        ((Map<String, Object>) r.get("latency")).forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
        return sb.toString();
    }
}
