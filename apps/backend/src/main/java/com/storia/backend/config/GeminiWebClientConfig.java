package com.storia.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class GeminiWebClientConfig {

    private final GeminiProperties properties;

    @Bean
    public WebClient geminiWebClient() {
        return WebClient.builder().baseUrl(properties.baseUrl()).build();
    }
}
