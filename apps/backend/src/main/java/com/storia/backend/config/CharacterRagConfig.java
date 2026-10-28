package com.storia.backend.config;

import com.storia.backend.lore.CharacterLoreCatalog;
import com.storia.backend.lore.CharacterLoreMetrics;
import com.storia.backend.lore.CharacterLoreService;
import com.storia.backend.rag.CachingEmbeddingProvider;
import com.storia.backend.rag.EmbeddingProvider;
import com.storia.backend.rag.GeminiEmbeddingProvider;
import com.storia.backend.rag.LocalHashingEmbeddingProvider;
import com.storia.backend.repository.CharacterRepository;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * 캐릭터 세계관 RAG 조립. 플래그가 꺼져 있으면 서비스는 만들어지되 어떤 문서도 읽지 않고 외부 호출도 하지 않는다.
 * 캐릭터마다 별도의 임베딩 제공자 인스턴스를 쓴다 (로컬 해싱 제공자는 코퍼스별 IDF 상태를 가지므로 공유하면 안 된다).
 */
@Configuration
public class CharacterRagConfig {

    @Bean
    public CharacterLoreMetrics characterLoreMetrics() {
        return new CharacterLoreMetrics();
    }

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService characterLoreExecutor() {
        // 데몬 스레드 2개 + 대기열 32. 가득 차면 거절되어 해당 요청은 세계관 없이 진행한다.
        return new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(32), runnable -> {
            Thread t = new Thread(runnable, "character-lore");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    public CharacterLoreService characterLoreService(CharacterRagProperties props, GeminiProperties gemini,
                                                     RagProperties rag, CharacterRepository characters,
                                                     CharacterLoreMetrics metrics,
                                                     ExecutorService characterLoreExecutor) {
        Supplier<EmbeddingProvider> factory = () -> {
            if (!props.useGemini()) {
                return new LocalHashingEmbeddingProvider();
            }
            String model = rag.geminiModel() == null || rag.geminiModel().isBlank() ? "gemini-embedding-2" : rag.geminiModel();
            int dimensions = rag.dimensions() == null ? 768 : rag.dimensions();
            String baseUrl = gemini.baseUrl() == null ? "https://generativelanguage.googleapis.com" : gemini.baseUrl();
            String cacheDir = rag.cacheDir() == null || rag.cacheDir().isBlank() ? ".storia-rag-cache" : rag.cacheDir();
            return new CachingEmbeddingProvider(
                    new GeminiEmbeddingProvider(gemini.apiKey(), baseUrl, model, dimensions), Path.of(cacheDir));
        };
        return new CharacterLoreService(props.isEnabled(), CharacterLoreCatalog.DEFAULT,
                id -> characters.findById(id).map(c -> c.getName()), factory, props.effectiveMinScore(),
                props.timeoutMsOrDefault(), characterLoreExecutor, metrics, getClass().getClassLoader());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp(ApplicationReadyEvent event) {
        event.getApplicationContext().getBean(CharacterLoreService.class).warmUpAsync();
    }
}
