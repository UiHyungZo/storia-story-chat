package com.storia.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storia.backend.config.GeminiProperties;
import com.storia.backend.entity.Message;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private static final Duration CHUNK_TIMEOUT = Duration.ofSeconds(60);

    private final WebClient geminiWebClient;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    public Flux<String> streamReply(String systemPrompt, List<Message> history) {
        if (!properties.isConfigured()) {
            return Flux.error(new IllegalStateException(
                    "GEMINI_API_KEY가 설정되지 않았습니다. 환경변수를 설정한 뒤 백엔드를 재시작하세요."));
        }

        Map<String, Object> requestBody = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", history.stream().map(this::toContent).toList());

        return geminiWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/models/{model}:streamGenerateContent")
                        .queryParam("alt", "sse")
                        .build(properties.model()))
                .header("x-goog-api-key", properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                })
                .timeout(CHUNK_TIMEOUT)
                .mapNotNull(ServerSentEvent::data)
                .map(this::extractText)
                .filter(text -> !text.isBlank());
    }

    private Map<String, Object> toContent(Message message) {
        String role = message.getRole() == Message.Role.USER ? "user" : "model";
        return Map.of("role", role, "parts", List.of(Map.of("text", message.getContent())));
    }

    private String extractText(String sseData) {
        try {
            JsonNode root = objectMapper.readTree(sseData);
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            return textNode.isMissingNode() ? "" : textNode.asText();
        } catch (Exception e) {
            log.warn("Failed to parse Gemini SSE chunk: {}", sseData, e);
            return "";
        }
    }
}
