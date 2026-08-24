package com.storia.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4's modular starters don't auto-configure a classic
 * com.fasterxml.jackson ObjectMapper bean in this app (only webmvc/websocket
 * starters are used, not the full "web" starter) — TtsService/SttService need
 * one directly, so it's declared explicitly here.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
