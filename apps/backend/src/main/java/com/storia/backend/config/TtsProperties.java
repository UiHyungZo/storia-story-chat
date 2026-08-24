package com.storia.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tts")
public record TtsProperties(String apiKey, String baseUrl, String languageCode, String voiceName) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
