package com.storia.backend.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.storia.backend.rag.DocumentLoader;
import com.storia.backend.rag.EmbeddingProvider;
import com.storia.backend.rag.KnowledgeBase;
import com.storia.backend.rag.KnowledgeSearchService;
import com.storia.backend.rag.KnowledgeSource;
import com.storia.backend.rag.LocalHashingEmbeddingProvider;
import com.storia.backend.rag.MarkdownChunker;
import com.storia.backend.rag.RagMetrics;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 프로토콜을 거치지 않는 어댑터 단위 테스트. 실제 MCP 프로토콜 검증은 {@link McpServerProtocolTest}가 맡는다. */
class KnowledgeMcpToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    static final List<KnowledgeSource> FIXTURE_SOURCES = List.of(
            new KnowledgeSource("voice-call", "docs/voice-call.md", "음성 통화 구조"),
            new KnowledgeSource("deploy", "docs/deploy.md", "배포 절차"));

    static Path fixtureRoot() {
        try {
            return Path.of(KnowledgeMcpToolTest.class.getResource("/eval/knowledge-fixtures").toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    static KnowledgeSearchService fixtureService(EmbeddingProvider provider) {
        return new KnowledgeSearchService(new KnowledgeBase(new DocumentLoader(fixtureRoot(), FIXTURE_SOURCES), provider,
                new MarkdownChunker(800), null), new RagMetrics());
    }

    private final ByteArrayOutputStream logBytes = new ByteArrayOutputStream();
    private final McpToolMetrics metrics = new McpToolMetrics(new PrintStream(logBytes, true, StandardCharsets.UTF_8));

    private KnowledgeMcpTool tool() {
        return new KnowledgeMcpTool(fixtureService(new LocalHashingEmbeddingProvider()), metrics);
    }

    private static Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void definition_declaresStrictSchemaAndReadOnlyAnnotations() {
        Tool t = KnowledgeMcpTool.definition();
        assertThat(t.name()).isEqualTo("searchStoriaKnowledge");
        assertThat(t.inputSchema()).containsEntry("additionalProperties", false).containsEntry("required", List.of("query"));
        assertThat(t.annotations().readOnlyHint()).isTrue();
        assertThat(t.annotations().destructiveHint()).isFalse();
        assertThat(t.annotations().openWorldHint()).isFalse();
        assertThat(t.annotations().idempotentHint()).isTrue();
        assertThat(t.outputSchema()).containsKey("properties");
        assertThat(t.inputSchema().get("properties")).isInstanceOf(Map.class);
    }

    @Test
    void validSearch_returnsSourcesAndMatchingStructuredAndTextContent() throws Exception {
        CallToolResult result = tool().handle(args("query", "음성 통화는 어떻게 동작해?", "topK", 2));

        assertThat(result.isError()).isFalse();
        @SuppressWarnings("unchecked")
        Map<String, Object> structured = (Map<String, Object>) result.structuredContent();
        assertThat(structured.get("status")).isEqualTo("OK");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hits = (List<Map<String, Object>>) structured.get("results");
        assertThat(hits.get(0).get("source")).isEqualTo("docs/voice-call.md");
        assertThat(((Number) hits.get(0).get("score")).doubleValue()).isGreaterThan(0.12);
        String text = ((TextContent) result.content().get(0)).text();
        assertThat(MAPPER.readTree(text)).isEqualTo(MAPPER.readTree(MAPPER.writeValueAsString(structured)));
        assertThat(text).doesNotContain("음성 통화는 어떻게 동작해?"); // 질의는 되돌려주지 않는다
    }

    @Test
    void noEvidenceIsNotAnError() {
        CallToolResult result = tool().handle(args("query", "오늘 서울 날씨 어때?"));
        assertThat(result.isError()).isFalse();
        assertThat(((Map<?, ?>) result.structuredContent()).get("status")).isEqualTo("NO_EVIDENCE");
        assertThat((List<?>) ((Map<?, ?>) result.structuredContent()).get("results")).isEmpty();
    }

    @Test
    void invalidArgumentsAreToolErrorsWithoutSearching() {
        for (Map<String, Object> bad : List.of(args(), args("query", ""), args("query", "   "), args("query", 5),
                args("query", "q", "topK", 0), args("query", "q", "topK", 6), args("query", "q", "topK", "3"),
                args("query", "q", "topK", 2.5), args("query", "q", "extra", true),
                args("query", "가".repeat(301)))) {
            CallToolResult result = tool().handle(bad);
            assertThat(result.isError()).as(bad.toString()).isTrue();
            assertThat(((Map<?, ?>) result.structuredContent()).get("status")).isEqualTo("INVALID_INPUT");
        }
        assertThat(tool().handle(null).isError()).isTrue();
    }

    @Test
    void integralDoubleTopKIsAccepted() {
        assertThat(tool().handle(args("query", "배포", "topK", 2.0)).isError()).isFalse();
    }

    @Test
    void slowSearchTimesOutAsToolError() {
        EmbeddingProvider slow = new LocalHashingEmbeddingProvider() {
            @Override
            public float[] embedQuery(String text) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return super.embedQuery(text);
            }
        };
        KnowledgeMcpTool slowTool = new KnowledgeMcpTool(fixtureService(slow), metrics, java.time.Duration.ofMillis(50));

        CallToolResult result = slowTool.handle(args("query", "음성 통화"));

        assertThat(result.isError()).isTrue();
        assertThat(((Map<?, ?>) result.structuredContent()).get("status")).isEqualTo("TIMEOUT");
        assertThat(metrics.summary().byErrorType()).containsEntry("timeout", 1L);
    }

    @Test
    void searchFailureIsGenericToolError_andMetricsHaveNoContent() {
        EmbeddingProvider failing = new LocalHashingEmbeddingProvider() {
            @Override
            public float[] embedQuery(String text) {
                throw new IllegalStateException("secret-host password=hunter2");
            }
        };
        CallToolResult result = new KnowledgeMcpTool(fixtureService(failing), metrics).handle(args("query", "비밀 질문 내용"));

        assertThat(result.isError()).isTrue();
        String text = ((TextContent) result.content().get(0)).text();
        assertThat(text).doesNotContain("secret-host").doesNotContain("hunter2");
        String log = logBytes.toString(StandardCharsets.UTF_8);
        assertThat(log).contains("\"event\":\"mcp_tool_call\"").contains("\"outcome\":\"ERROR\"")
                .doesNotContain("비밀 질문").doesNotContain("hunter2");
    }

    @Test
    void metricsSummarizeOutcomesAndLatency() {
        KnowledgeMcpTool t = tool();
        t.handle(args("query", "음성 통화"));
        t.handle(args("query", "서울 날씨"));
        t.handle(args());

        McpToolMetrics.Summary s = metrics.summary();
        assertThat(s.calls()).isEqualTo(3);
        assertThat(s.byOutcome()).containsEntry("OK", 1L).containsEntry("NO_EVIDENCE", 1L).containsEntry("INVALID_INPUT", 1L);
        assertThat(s.byErrorType()).containsEntry("invalid_arguments", 1L);
        assertThat(s.avgMs()).isNotNull();
        assertThat(s.p95Ms()).isNotNull();
    }
}
