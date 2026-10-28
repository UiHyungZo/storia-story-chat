package com.storia.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.storia.backend.config.GeminiProperties;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * 관리자 전용 Gemini Function Calling 클라이언트 (비스트리밍 {@code generateContent}).
 * 채팅용 {@link com.storia.backend.service.GeminiService}와 분리되어 있어 기존 채팅 경로에 영향이 없다.
 * 요청/응답 본문과 API 키는 로그에 남기지 않는다.
 */
@Component
@Slf4j
public class GeminiToolClient {

    static final Duration MAX_CALL_TIMEOUT = Duration.ofSeconds(60);

    private final WebClient geminiWebClient;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiToolClient(WebClient geminiWebClient, GeminiProperties properties, ObjectMapper objectMapper) {
        this.geminiWebClient = geminiWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * @param contents 대화 내용(사용자 입력, 모델의 functionCall 파트, functionResponse 파트). 호출자가 소유한다.
     * @param functionDeclarations {@link AdminToolRegistry#declarations()}
     * @return 응답 JSON 전체
     * @throws IllegalStateException 키 미설정, HTTP 오류, 타임아웃 등 (GlobalExceptionHandler가 502로 매핑)
     */
    public JsonNode generate(String systemInstruction, ArrayNode contents, ArrayNode functionDeclarations,
                             Duration timeout) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("GEMINI_API_KEY가 설정되지 않았습니다.");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", systemInstruction);
        body.set("contents", contents);
        body.putArray("tools").addObject().set("functionDeclarations", functionDeclarations);
        body.putObject("toolConfig").putObject("functionCallingConfig").put("mode", "AUTO");

        Duration effective = timeout.compareTo(MAX_CALL_TIMEOUT) > 0 ? MAX_CALL_TIMEOUT : timeout;
        try {
            String response = geminiWebClient.post()
                    .uri(uri -> uri.path("/v1beta/models/{model}:generateContent").build(properties.model()))
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(effective);
            return objectMapper.readTree(response == null ? "{}" : response);
        } catch (WebClientResponseException e) {
            // 응답 본문에는 요청 내용이 반영될 수 있으므로 상태 코드만 남긴다.
            log.warn("Gemini tool call failed: status={}", e.getStatusCode().value());
            throw new IllegalStateException("Gemini 호출 실패: " + e.getStatusCode().value());
        } catch (Exception e) {
            log.warn("Gemini tool call failed: {}", e.getClass().getSimpleName());
            throw new IllegalStateException("Gemini 호출 실패");
        }
    }
}
