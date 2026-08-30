package com.storia.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class SttWebClientConfig {

    private final SttProperties properties;

    /** A voice turn's recognition response can carry a long transcript; keep the same
     *  headroom as the TTS client rather than WebClient's default 256 KB buffer. */
    private static final int MAX_IN_MEMORY_BYTES = 16 * 1024 * 1024;

    @Bean
    public WebClient sttWebClient() {
        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .build();
    }
}
