package com.storia.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stt")
public record SttProperties(
        String apiKey, String baseUrl, String languageCode, int sampleRateHertz, int audioChannelCount) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
