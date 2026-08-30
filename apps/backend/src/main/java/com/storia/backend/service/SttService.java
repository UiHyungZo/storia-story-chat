package com.storia.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storia.backend.config.SttProperties;
import java.util.Base64;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Batch STT via Google Cloud Speech-to-Text (PRD 3.9 음성 통화 — 축소판 A안).
 * Takes the raw LINEAR16 PCM that a LiveKit Track Egress streamed to us for one
 * voice turn (see VoiceEgressWebSocketHandler) and returns the transcript, or
 * null if STT isn't configured / recognition finds nothing — same
 * graceful-degradation posture as GeminiService/TtsService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SttService {

    private final WebClient sttWebClient;
    private final SttProperties properties;
    private final ObjectMapper objectMapper;

    public String transcribe(byte[] linear16Audio) {
        if (!properties.isConfigured() || linear16Audio.length == 0) {
            return null;
        }

        Map<String, Object> requestBody = Map.of(
                "config", Map.of(
                        "encoding", "LINEAR16",
                        "sampleRateHertz", properties.sampleRateHertz(),
                        // LiveKit Track Egress gives us interleaved stereo; tell Google so it
                        // doesn't read the interleaved samples as a single garbled mono stream.
                        "audioChannelCount", properties.audioChannelCount(),
                        "languageCode", properties.languageCode()),
                "audio", Map.of("content", Base64.getEncoder().encodeToString(linear16Audio)));

        try {
            String response = sttWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/speech:recognize")
                            .queryParam("key", properties.apiKey())
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode transcript = root.path("results").path(0).path("alternatives").path(0).path("transcript");
            return transcript.isMissingNode() ? null : transcript.asText();
        } catch (Exception e) {
            log.warn("STT 인식 실패", e);
            return null;
        }
    }
}
