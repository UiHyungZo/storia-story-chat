package com.storia.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.storia.backend.rag.CachingEmbeddingProvider;
import com.storia.backend.rag.EmbeddingProvider;
import com.storia.backend.rag.LocalHashingEmbeddingProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RagConfigTest {

    private final RagConfig config = new RagConfig();
    private final GeminiProperties gemini = new GeminiProperties("test-key", "gemini-3.6-flash", "http://127.0.0.1:1");

    @Test
    void defaultProviderIsLocalAndNeedsNoExternalCalls() {
        EmbeddingProvider p = config.ragEmbeddingProvider(new RagProperties(null, null, null, null, null, null), gemini);
        assertThat(p).isInstanceOf(LocalHashingEmbeddingProvider.class);
        assertThat(p.semantic()).isFalse();
    }

    @Test
    void geminiProviderIsOptInAndCached(@TempDir java.nio.file.Path dir) {
        EmbeddingProvider p = config.ragEmbeddingProvider(
                new RagProperties("../..", "gemini", dir.toString(), "gemini-embedding-001", 256, null), gemini);
        assertThat(p).isInstanceOf(CachingEmbeddingProvider.class);
        assertThat(p.id()).isEqualTo("gemini:gemini-embedding-001:256");
        assertThat(p.semantic()).isTrue();
    }

    @Test
    void docsRootDefaultsToRepositoryRootRelativeToBackend() {
        assertThat(new RagProperties(null, null, null, null, null, null).docsRootOrDefault()).isEqualTo("../..");
        assertThat(new RagProperties("  ", null, null, null, null, null).docsRootOrDefault()).isEqualTo("../..");
    }
}
