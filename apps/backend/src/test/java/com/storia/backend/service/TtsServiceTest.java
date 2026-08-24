package com.storia.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.storia.backend.config.TtsProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * TTS_API_KEY 미설정 시 우아하게 저하되는지(graceful degradation) 검증.
 * 실제 자격증명이 없는 이 머신에서도 그 동작만큼은 실제로 확인할 수 있다.
 */
@ExtendWith(MockitoExtension.class)
class TtsServiceTest {

    @Mock private WebClient ttsWebClient;

    @Test
    void synthesize_returnsNullWithoutCallingWebClient_whenNotConfigured() {
        TtsProperties unconfigured = new TtsProperties("", "https://texttospeech.googleapis.com", "ko-KR", "ko-KR-Standard-A");
        TtsService service = new TtsService(ttsWebClient, unconfigured, new ObjectMapper());

        byte[] result = service.synthesize("안녕하세요", null);

        assertThat(result).isNull();
        verifyNoInteractions(ttsWebClient);
    }

    @Test
    void synthesize_treatsBlankApiKeyAsUnconfigured() {
        TtsProperties blankKey = new TtsProperties("   ", "https://texttospeech.googleapis.com", "ko-KR", "ko-KR-Standard-A");
        TtsService service = new TtsService(ttsWebClient, blankKey, new ObjectMapper());

        assertThat(service.synthesize("hello", "ko-KR-Standard-B")).isNull();
        verifyNoInteractions(ttsWebClient);
    }
}
