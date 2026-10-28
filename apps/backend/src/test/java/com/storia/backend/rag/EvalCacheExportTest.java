package com.storia.backend.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * 평가 캐시 → 관리자 RAG 캐시 변환. 원격 호출 없음 — 제공자는 닫힌 로컬 포트를 가리키므로 캐시가 빗나가면 임베딩이
 * 호출되지 않고 예외가 난다(= "API 호출 0"을 검증하는 방식).
 */
class EvalCacheExportTest {

    private static final String DEAD_URL = "http://127.0.0.1:9";

    @TempDir
    Path tmp;

    private static GeminiEmbeddingProvider offline(int dims) {
        return new GeminiEmbeddingProvider("unused", DEAD_URL, "gemini-embedding-2", dims);
    }

    private static String encode(float[] v) {
        ByteBuffer buffer = ByteBuffer.allocate(v.length * 4);
        buffer.asFloatBuffer().put(v);
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    private static float[] unit(int dims, int hot) {
        float[] v = new float[dims];
        v[hot] = 1f;
        return v;
    }

    private Path evalCache(GeminiEmbeddingProvider g, List<String> texts) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("version", 1);
        root.put("signature", g.documentRequestSignature());
        root.put("dims", g.dimensions());
        ObjectNode vectors = root.putObject("vectors");
        for (int i = 0; i < texts.size(); i++) {
            vectors.put(MarkdownChunker.sha256(g.documentRequestSignature() + "\n" + g.documentText(texts.get(i))),
                    encode(unit(g.dimensions(), i)));
        }
        Path dir = tmp.resolve("eval");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("eval-batch-cache-"
                + MarkdownChunker.sha256(g.documentRequestSignature()).substring(0, 16) + ".json"),
                mapper.writeValueAsString(root), StandardCharsets.UTF_8);
        return dir;
    }

    @Test
    void exportedCacheServesSameVectorsWithoutRemoteCalls() throws Exception {
        GeminiEmbeddingProvider g = offline(8);
        List<String> texts = List.of("alpha 문서", "beta 문서", "gamma 문서");
        Path eval = evalCache(g, texts);
        Path target = tmp.resolve("rag");

        EvalCacheExport.Result r = EvalCacheExport.export(g, eval, target, texts, true);
        assertThat(r.matched()).isEqualTo(3);
        assertThat(r.missing()).isZero();
        assertThat(r.targetFile()).isEqualTo("embeddings-gemini_gemini-embedding-2_8.json");

        CachingEmbeddingProvider cached = new CachingEmbeddingProvider(offline(8), target);
        List<float[]> vectors = cached.embedDocuments(texts);
        assertThat(cached.lastStats()).containsExactly(3, 0);
        for (int i = 0; i < texts.size(); i++) {
            assertThat(vectors.get(i)).containsExactly(unit(8, i));
        }
    }

    @Test
    void changedTextIsReportedMissingAndNotExported() throws Exception {
        GeminiEmbeddingProvider g = offline(8);
        Path eval = evalCache(g, List.of("alpha", "beta"));
        EvalCacheExport.Result r = EvalCacheExport.export(g, eval, tmp.resolve("rag"), List.of("alpha", "beta 수정"), false);
        assertThat(r.matched()).isEqualTo(1);
        assertThat(r.missing()).isEqualTo(1);
        assertThat(r.unusedEvalEntries()).isEqualTo(1);
        assertThat(r.written()).isFalse();
        assertThat(tmp.resolve("rag")).doesNotExist();
    }

    @Test
    void refusesMismatchedDimensionsAndExistingTarget() throws Exception {
        GeminiEmbeddingProvider g = offline(8);
        List<String> texts = List.of("alpha");
        Path eval = evalCache(g, texts);
        // 다른 차원 제공자는 식별자가 달라 평가 파일을 찾지 못한다.
        assertThatThrownBy(() -> EvalCacheExport.export(offline(16), eval, tmp.resolve("x"), texts, true))
                .isInstanceOf(java.nio.file.NoSuchFileException.class);

        Path target = tmp.resolve("rag");
        EvalCacheExport.export(g, eval, target, texts, true);
        assertThatThrownBy(() -> EvalCacheExport.export(g, eval, target, texts, true))
                .hasMessageContaining("덮어쓰지 않음");
    }

    /**
     * 실제 평가 캐시(build/rag-live-cache)와 현재 코퍼스로 검증. 결과는 임시 디렉터리에만 쓰고, 관리자 RAG 인덱스를
     * 원격 호출 없이 만들 수 있는지 확인한다. {@code EVAL_CACHE_EXPORT_TARGET}이 있으면 그 디렉터리에도 쓴다(덮어쓰기 거부).
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_EVAL_CACHE_EXPORT", matches = "true")
    void realEvalCacheBuildsAdminIndexOffline() throws Exception {
        Path repoRoot = Path.of("../..").toAbsolutePath().normalize();
        GeminiEmbeddingProvider g = offline(768);
        List<String> corpus = RagLiveEvalTest.documentTexts(repoRoot);
        Path check = tmp.resolve("rag");
        EvalCacheExport.Result r = EvalCacheExport.export(g, Path.of("build/rag-live-cache"), check, corpus, true);
        System.out.println("eval cache export check: " + r);
        assertThat(r.missing()).isZero();

        CachingEmbeddingProvider cached = new CachingEmbeddingProvider(offline(768), check);
        KnowledgeBase kb = new KnowledgeBase(new DocumentLoader(repoRoot, KnowledgeSources.DEFAULT), cached,
                new MarkdownChunker(800), 0.75);
        KnowledgeBase.Snapshot s = kb.rebuild();
        System.out.println("offline admin index: chunks=" + s.chunks() + " hits/misses="
                + java.util.Arrays.toString(cached.lastStats()) + " provider=" + s.providerId() + " minScore=" + s.minScore());
        assertThat(cached.lastStats()).containsExactly(corpus.size(), 0);

        String target = System.getenv("EVAL_CACHE_EXPORT_TARGET");
        if (target != null && !target.isBlank()) {
            System.out.println("export: " + EvalCacheExport.export(g, Path.of("build/rag-live-cache"), Path.of(target),
                    corpus, true));
        }
    }
}
