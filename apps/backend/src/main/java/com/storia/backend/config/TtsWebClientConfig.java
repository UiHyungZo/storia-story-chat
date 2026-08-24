package com.storia.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class TtsWebClientConfig {

    private final TtsProperties properties;

    @Bean
    public WebClient ttsWebClient() {
        return WebClient.builder().baseUrl(properties.baseUrl()).build();
    }
}
