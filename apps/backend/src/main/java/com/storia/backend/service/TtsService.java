package com.storia.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storia.backend.config.TtsProperties;
import java.util.Base64;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Synthesizes speech via Google Cloud Text-to-Speech (PRD 3.9 — 음성 통화 B안).
 * Returns null (not an exception) if TTS isn't configured or synthesis fails, so
 * callers can fall back to text-only rather than surfacing a 500 — same
 * graceful-degradation posture as GeminiService/PushNotificationService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TtsService {

    private final WebClient ttsWebClient;
    private final TtsProperties properties;
    private final ObjectMapper objectMapper;

    /** voiceName이 null/빈 값이면 TtsProperties#voiceName(전역 기본값)으로 폴백한다. */
    public byte[] synthesize(String text, String voiceName) {
        if (!properties.isConfigured()) {
            return null;
        }
        String resolvedVoiceName = (voiceName == null || voiceName.isBlank())
                ? properties.voiceName()
                : voiceName;

        Map<String, Object> requestBody = Map.of(
                "input", Map.of("text", text),
                "voice", Map.of(
                        "languageCode", properties.languageCode(),
                        "name", resolvedVoiceName),
                "audioConfig", Map.of("audioEncoding", "MP3"));

        try {
            String response = ttsWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/text:synthesize")
                            .queryParam("key", properties.apiKey())
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode audioContent = root.path("audioContent");
            return audioContent.isMissingNode() ? null : Base64.getDecoder().decode(audioContent.asText());
        } catch (Exception e) {
            log.warn("TTS 합성 실패", e);
            return null;
        }
    }
}
