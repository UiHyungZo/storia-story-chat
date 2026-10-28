package com.storia.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.storia.backend.lore.CharacterLoreMetrics;
import com.storia.backend.lore.CharacterLoreService;
import com.storia.backend.lore.LoreStatus;
import com.storia.backend.repository.CharacterRepository;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;

class CharacterRagConfigTest {

    private final GeminiProperties gemini = new GeminiProperties("test-key", "gemini-3.6-flash", "http://127.0.0.1:1");
    private final RagProperties rag = new RagProperties(null, null, null, null, null, null);

    @Test
    void featureFlagDefaultsToOffAndLocalProvider() {
        CharacterRagProperties props = new CharacterRagProperties(null, null, null, null, null);
        assertThat(props.isEnabled()).isFalse();
        assertThat(props.useGemini()).isFalse();
        assertThat(props.timeoutMsOrDefault()).isEqualTo(800);
        assertThat(props.topKOrDefault()).isEqualTo(3);
        assertThat(props.effectiveMinScore()).isEqualTo(CharacterRagProperties.LOCAL_MIN_SCORE);
        // Gemini는 아직 보정하지 않았으므로 임계값을 임의로 정하지 않고 제공자 기본값에 맡긴다.
        assertThat(new CharacterRagProperties(true, "gemini", null, null, null).effectiveMinScore()).isNull();
        assertThat(new CharacterRagProperties(true, "local", null, null, 0.3).effectiveMinScore()).isEqualTo(0.3);
    }

    @Test
    void disabledServiceNeverTouchesTheRepositoryOrExecutor() {
        CharacterRagConfig config = new CharacterRagConfig();
        CharacterRepository repository = mock(CharacterRepository.class);
        ExecutorService executor = config.characterLoreExecutor();
        try {
            CharacterLoreService service = config.characterLoreService(
                    new CharacterRagProperties(false, null, null, null, null), gemini, rag, repository,
                    new CharacterLoreMetrics(), executor);
            assertThat(service.enabled()).isFalse();
            assertThat(service.searchCharacterLore(1L, "함장 이름이 뭐야?", 3).status()).isEqualTo(LoreStatus.DISABLED);
            verifyNoInteractions(repository);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void geminiProviderIsOnlyChosenExplicitlyAndIsNotCalledWhenDisabled() {
        // 활성화 + gemini 설정이어도 이 테스트는 검색을 호출하지 않으므로 외부 호출이 없다.
        CharacterRagConfig config = new CharacterRagConfig();
        ExecutorService executor = config.characterLoreExecutor();
        try {
            CharacterLoreService service = config.characterLoreService(
                    new CharacterRagProperties(true, "gemini", null, null, null), gemini, rag,
                    mock(CharacterRepository.class), new CharacterLoreMetrics(), executor);
            assertThat(service.enabled()).isTrue();
            assertThat(service.summary().indexes().values()).allSatisfy(s -> assertThat(s.state()).isEqualTo("NOT_BUILT"));
        } finally {
            executor.shutdownNow();
        }
    }
}
