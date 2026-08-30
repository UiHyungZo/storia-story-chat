package com.storia.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.storia.backend.config.SttProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(MockitoExtension.class)
class SttServiceTest {

    @Mock private WebClient sttWebClient;

    @Test
    void transcribe_returnsNullWithoutCallingWebClient_whenNotConfigured() {
        SttProperties unconfigured = new SttProperties("", "https://speech.googleapis.com", "ko-KR", 48000, 2);
        SttService service = new SttService(sttWebClient, unconfigured, new ObjectMapper());

        String result = service.transcribe(new byte[] {1, 2, 3});

        assertThat(result).isNull();
        verifyNoInteractions(sttWebClient);
    }

    @Test
    void transcribe_returnsNullWithoutCallingWebClient_whenAudioIsEmpty() {
        SttProperties configured = new SttProperties("real-key", "https://speech.googleapis.com", "ko-KR", 48000, 2);
        SttService service = new SttService(sttWebClient, configured, new ObjectMapper());

        String result = service.transcribe(new byte[0]);

        assertThat(result).isNull();
        verifyNoInteractions(sttWebClient);
    }
}
