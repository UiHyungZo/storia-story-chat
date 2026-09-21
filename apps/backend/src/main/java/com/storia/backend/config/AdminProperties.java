package com.storia.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "admin")
public record AdminProperties(String password) {

    public boolean isConfigured() {
        return password != null && !password.isBlank();
    }
}
