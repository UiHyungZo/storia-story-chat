package com.storia.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "firebase")
public record FcmProperties(String credentialsPath) {

    public boolean isConfigured() {
        return credentialsPath != null && !credentialsPath.isBlank();
    }
}
