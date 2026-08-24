package com.storia.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(String apiKey, String model, String baseUrl) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
