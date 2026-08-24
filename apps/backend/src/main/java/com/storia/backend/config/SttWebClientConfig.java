package com.storia.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class SttWebClientConfig {

    private final SttProperties properties;

    @Bean
    public WebClient sttWebClient() {
        return WebClient.builder().baseUrl(properties.baseUrl()).build();
    }
}
