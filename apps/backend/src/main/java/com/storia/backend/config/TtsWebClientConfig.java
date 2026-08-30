package com.storia.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class TtsWebClientConfig {

    private final TtsProperties properties;

    /** Google TTS returns the whole MP3 base64-encoded inside one JSON body; a longer
     *  reply easily blows past WebClient's default 256 KB in-memory buffer, so raise it. */
    private static final int MAX_IN_MEMORY_BYTES = 16 * 1024 * 1024;

    @Bean
    public WebClient ttsWebClient() {
        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .build();
    }
}
