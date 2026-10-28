package com.storia.backend.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * SDK 클라이언트 없이 원시 JSON-RPC 줄을 직접 보내 stdio 규칙을 확인한다: stdout에는 유효한 MCP 메시지만 나오고
 * 로그는 stderr로 가며, stdin을 닫으면 서버가 종료된다.
 */
class RawStdioProtocolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void stdoutContainsOnlyJsonRpcMessages_logsGoToStderr_andServerExitsOnStdinClose() throws Exception {
        Path root = Files.createTempDirectory("storia-mcp-raw");
        Files.createDirectories(root.resolve("docs"));
        Files.writeString(root.resolve("docs/deployment.md"), "# 배포\n\n## TestFlight\nTestFlight 배포는 fastlane으로 진행합니다. 충분히 긴 설명 문장입니다.\n");

        ProcessBuilder builder = new ProcessBuilder(McpServerProtocolTest.launcher().toString());
        builder.environment().put("STORIA_DOCS_ROOT", root.toString());
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        Process process = builder.start();

        try (Writer stdin = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
             BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {

            send(stdin, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\","
                    + "\"capabilities\":{},\"clientInfo\":{\"name\":\"raw\",\"version\":\"0\"}}}");
            send(stdin, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
            send(stdin, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
            send(stdin, "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"searchStoriaKnowledge\","
                    + "\"arguments\":{\"query\":\"TestFlight 배포\"}}}");
            send(stdin, "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"nope\",\"arguments\":{}}}");
            send(stdin, "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"no/such/method\"}");

            List<JsonNode> responses = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String line = stdout.readLine();
                assertThat(line).as("응답 " + i).isNotNull();
                JsonNode node = MAPPER.readTree(line); // 유효한 JSON이 아니면 여기서 실패 = stdout 오염
                assertThat(node.get("jsonrpc").asText()).isEqualTo("2.0");
                responses.add(node);
            }
            assertThat(responses).extracting(n -> n.get("id").asInt()).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
            JsonNode init = byId(responses, 1);
            assertThat(init.at("/result/protocolVersion").asText()).isEqualTo("2025-11-25");
            assertThat(byId(responses, 2).at("/result/tools/0/name").asText()).isEqualTo("searchStoriaKnowledge");
            assertThat(byId(responses, 3).at("/result/isError").asBoolean()).isFalse();
            assertThat(byId(responses, 4).has("error")).isTrue();
            assertThat(byId(responses, 5).at("/error/code").asInt()).isEqualTo(-32601);

            stdin.close(); // 클라이언트 종료 → 서버도 종료되어야 한다
            assertThat(process.waitFor(15, TimeUnit.SECONDS)).as("stdin 종료 후 서버 종료").isTrue();
            assertThat(stdout.readLine()).as("종료 후 추가 stdout 출력 없음").isNull();
            StringBuilder err = new StringBuilder();
            for (String l; (l = stderr.readLine()) != null; ) {
                err.append(l).append('\n');
            }
            assertThat(err.toString()).contains("storia-mcp-server ready").contains("mcp_tool_call");
        } finally {
            process.destroyForcibly();
        }
    }

    private static void send(Writer stdin, String json) throws IOException {
        stdin.write(json + "\n");
        stdin.flush();
    }

    private static JsonNode byId(List<JsonNode> responses, int id) {
        return responses.stream().filter(n -> n.get("id").asInt() == id).findFirst().orElseThrow();
    }
}
