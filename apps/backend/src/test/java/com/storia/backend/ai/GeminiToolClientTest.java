package com.storia.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.storia.backend.config.GeminiProperties;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** 외부 호출 없이 ExchangeFunction 스텁으로 요청 형식과 오류 처리를 검증한다. */
class GeminiToolClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String API_KEY = "test-key-should-not-leak";

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    private GeminiToolClient clientReturning(HttpStatus status, String body, String apiKey) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://gemini.test")
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(status)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(body).build());
                }).build();
        return new GeminiToolClient(webClient, new GeminiProperties(apiKey, "test-model", "http://gemini.test"), MAPPER);
    }

    private String bodyOf(ClientRequest request) {
        MockClientHttpRequest mock = new MockClientHttpRequest(HttpMethod.POST, "/");
        List<HttpMessageWriter<?>> writers = ExchangeStrategies.withDefaults().messageWriters();
        request.body().insert(mock, new BodyInserter.Context() {
            @Override public List<HttpMessageWriter<?>> messageWriters() { return writers; }
            @Override public Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest() { return Optional.empty(); }
            @Override public Map<String, Object> hints() { return Map.of(); }
        }).block();
        return mock.getBodyAsString().block();
    }

    @Test
    void generate_sendsFunctionDeclarationsAndKeyHeader_andParsesResponse() throws Exception {
        GeminiToolClient client = clientReturning(HttpStatus.OK,
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}", API_KEY);
        ArrayNode contents = MAPPER.createArrayNode();
        contents.addObject().put("role", "user").putArray("parts").addObject().put("text", "질문");
        ArrayNode declarations = new AdminToolRegistry(null, null, new ProposalStore(), null, MAPPER).declarations();

        JsonNode response = client.generate("시스템", contents, declarations, Duration.ofSeconds(5));

        assertThat(response.at("/candidates/0/content/parts/0/text").asText()).isEqualTo("ok");
        ClientRequest request = captured.get();
        assertThat(request.method()).isEqualTo(HttpMethod.POST);
        assertThat(request.url().getPath()).isEqualTo("/v1beta/models/test-model:generateContent");
        assertThat(request.headers().getFirst("x-goog-api-key")).isEqualTo(API_KEY);
        assertThat(request.url().toString()).doesNotContain(API_KEY);

        JsonNode body = MAPPER.readTree(bodyOf(request));
        assertThat(body.at("/systemInstruction/parts/0/text").asText()).isEqualTo("시스템");
        assertThat(body.at("/tools/0/functionDeclarations/0/name").asText()).isEqualTo("getCharacterConfig");
        assertThat(body.at("/tools/0/functionDeclarations/1/name").asText()).isEqualTo("getConversationLogs");
        assertThat(body.at("/tools/0/functionDeclarations/2/name").asText()).isEqualTo("searchStoriaKnowledge");
        assertThat(body.at("/tools/0/functionDeclarations/3/name").asText()).isEqualTo("proposeCharacterConfigChange");
        assertThat(body.at("/toolConfig/functionCallingConfig/mode").asText()).isEqualTo("AUTO");
        assertThat(body.at("/contents/0/parts/0/text").asText()).isEqualTo("질문");
        assertThat(body.toString()).doesNotContain(API_KEY);
    }

    @Test
    void generate_httpError_throwsIllegalStateWithoutLeakingKeyOrBody() {
        GeminiToolClient client = clientReturning(HttpStatus.BAD_REQUEST,
                "{\"error\":{\"message\":\"echo of secret request " + API_KEY + "\"}}", API_KEY);

        assertThatThrownBy(() -> client.generate("s", MAPPER.createArrayNode(), MAPPER.createArrayNode(), Duration.ofSeconds(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("400")
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    void generate_withoutApiKey_failsBeforeAnyHttpCall() {
        GeminiToolClient client = clientReturning(HttpStatus.OK, "{}", "");

        assertThatThrownBy(() -> client.generate("s", MAPPER.createArrayNode(), MAPPER.createArrayNode(), Duration.ofSeconds(5)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(captured.get()).isNull();
    }
}
