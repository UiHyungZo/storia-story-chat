package com.storia.backend.config;

import com.storia.backend.rag.CachingEmbeddingProvider;
import com.storia.backend.rag.DocumentLoader;
import com.storia.backend.rag.EmbeddingProvider;
import com.storia.backend.rag.GeminiEmbeddingProvider;
import com.storia.backend.rag.KnowledgeBase;
import com.storia.backend.rag.KnowledgeSearchService;
import com.storia.backend.rag.KnowledgeSources;
import com.storia.backend.rag.LocalHashingEmbeddingProvider;
import com.storia.backend.rag.MarkdownChunker;
import com.storia.backend.rag.RagMetrics;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 구성요소 조립. 인덱스는 첫 검색(또는 재인덱싱 요청) 때 만들어지므로 앱 기동 시에는 파일 읽기나 외부
 * 임베딩 호출이 일어나지 않는다. 기본 제공자는 외부 호출이 없는 {@code local}이다.
 */
@Configuration
public class RagConfig {

    @Bean
    public RagMetrics ragMetrics() {
        return new RagMetrics();
    }

    @Bean
    public EmbeddingProvider ragEmbeddingProvider(RagProperties rag, GeminiProperties gemini) {
        if (!rag.useGemini()) {
            return new LocalHashingEmbeddingProvider();
        }
        String model = rag.geminiModel() == null || rag.geminiModel().isBlank() ? "gemini-embedding-2" : rag.geminiModel();
        int dimensions = rag.dimensions() == null ? 768 : rag.dimensions();
        String baseUrl = gemini.baseUrl() == null ? "https://generativelanguage.googleapis.com" : gemini.baseUrl();
        EmbeddingProvider remote = new GeminiEmbeddingProvider(gemini.apiKey(), baseUrl, model, dimensions);
        String cacheDir = rag.cacheDir() == null || rag.cacheDir().isBlank() ? ".storia-rag-cache" : rag.cacheDir();
        return new CachingEmbeddingProvider(remote, Path.of(cacheDir));
    }

    @Bean
    public KnowledgeBase ragKnowledgeBase(RagProperties rag, EmbeddingProvider ragEmbeddingProvider) {
        DocumentLoader loader = new DocumentLoader(Path.of(rag.docsRootOrDefault()), KnowledgeSources.DEFAULT);
        return new KnowledgeBase(loader, ragEmbeddingProvider, new MarkdownChunker(800), rag.minScore());
    }

    @Bean
    public KnowledgeSearchService knowledgeSearchService(KnowledgeBase ragKnowledgeBase, RagMetrics ragMetrics) {
        return new KnowledgeSearchService(ragKnowledgeBase, ragMetrics, true);
    }
}
