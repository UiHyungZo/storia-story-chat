package com.storia.backend.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EmbeddingProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ---- LocalHashingEmbeddingProvider ----

    private static double dot(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) {
            s += (double) a[i] * b[i];
        }
        return s;
    }

    @Test
    void local_isDeterministicNormalizedAndMarkedNonSemantic() {
        LocalHashingEmbeddingProvider p = new LocalHashingEmbeddingProvider();
        float[] a = p.embedQuery("음성 통화 구조");
        float[] b = new LocalHashingEmbeddingProvider().embedQuery("음성 통화 구조");

        assertThat(a).isEqualTo(b);
        assertThat(dot(a, a)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
        assertThat(p.semantic()).isFalse();
        assertThat(p.id()).isEqualTo("local-hashing-v1");
    }

    @Test
    void local_lexicalOverlapRanksHigher_butParaphraseWithoutOverlapDoesNot() {
        LocalHashingEmbeddingProvider p = new LocalHashingEmbeddingProvider();
        List<String> corpus = List.of("음성 통화는 LiveKit으로 연결됩니다", "데이터베이스는 MariaDB를 사용합니다", "배포는 TestFlight로 합니다");
        p.prepare(corpus);
        List<float[]> docs = p.embedDocuments(corpus);

        float[] q = p.embedQuery("음성 통화 연결");
        assertThat(dot(q, docs.get(0))).isGreaterThan(dot(q, docs.get(1))).isGreaterThan(dot(q, docs.get(2)));

        // 한계: 어휘 중첩이 없는 동의어 질문은 의미를 알아보지 못한다 (의미 임베딩이 아님을 보여주는 테스트).
        float[] paraphrase = p.embedQuery("전화 대화 방식");
        assertThat(dot(paraphrase, docs.get(0))).isLessThan(0.15);
    }

    @Test
    void local_emptyTextGivesZeroVectorWithoutNaN() {
        float[] v = new LocalHashingEmbeddingProvider().embedQuery("   ");
        for (float x : v) {
            assertThat(x).isZero();
        }
    }

    // ---- GeminiEmbeddingProvider (로컬 Mock HTTP 서버 — 실제 Gemini 호출 아님) ----

    private String startServer(java.util.function.Function<JsonNode, String> responder, List<String> paths,
                               List<JsonNode> bodies, List<String> keys, int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            keys.add(String.valueOf(exchange.getRequestHeaders().getFirst("x-goog-api-key")));
            JsonNode body = MAPPER.readTree(exchange.getRequestBody().readAllBytes());
            bodies.add(body);
            byte[] out = responder.apply(body).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String batchResponse(JsonNode body) {
        StringBuilder sb = new StringBuilder("{\"embeddings\":[");
        int n = body.path("requests").size();
        for (int i = 0; i < n; i++) {
            sb.append(i == 0 ? "" : ",").append("{\"values\":[3.0,4.0,0.0]}");
        }
        return sb.append("]}").toString();
    }

    @Test
    void gemini_embedding001_sendsTaskTypeBatchesAndNormalizes() throws Exception {
        List<String> paths = new CopyOnWriteArrayList<>();
        List<JsonNode> bodies = new CopyOnWriteArrayList<>();
        List<String> keys = new CopyOnWriteArrayList<>();
        String url = startServer(GeminiEmbeddingProviderTestHelper::unused, paths, bodies, keys, 200);
        server.removeContext("/");
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            keys.add(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            JsonNode body = MAPPER.readTree(exchange.getRequestBody().readAllBytes());
            bodies.add(body);
            String json = exchange.getRequestURI().getPath().endsWith(":embedContent")
                    ? "{\"embedding\":{\"values\":[3.0,4.0,0.0]}}" : batchResponse(body);
            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        GeminiEmbeddingProvider p = new GeminiEmbeddingProvider("test-key", url, "gemini-embedding-001", 3);

        List<String> texts = new ArrayList<>();
        for (int i = 0; i < GeminiEmbeddingProvider.BATCH_SIZE + 5; i++) {
            texts.add("문서 " + i);
        }
        List<float[]> docs = p.embedDocuments(texts);
        float[] q = p.embedQuery("질문");

        assertThat(docs).hasSize(texts.size());
        assertThat(docs.get(0)[0]).isCloseTo(0.6f, org.assertj.core.data.Offset.offset(1e-5f));
        assertThat(docs.get(0)[1]).isCloseTo(0.8f, org.assertj.core.data.Offset.offset(1e-5f));
        assertThat(q[1]).isCloseTo(0.8f, org.assertj.core.data.Offset.offset(1e-5f));
        assertThat(paths).containsExactly("/v1beta/models/gemini-embedding-001:batchEmbedContents",
                "/v1beta/models/gemini-embedding-001:batchEmbedContents", "/v1beta/models/gemini-embedding-001:embedContent");
        assertThat(keys).containsOnly("test-key");
        assertThat(bodies.get(0).path("requests").size()).isEqualTo(GeminiEmbeddingProvider.BATCH_SIZE);
        JsonNode first = bodies.get(0).path("requests").get(0);
        assertThat(first.path("taskType").asText()).isEqualTo("RETRIEVAL_DOCUMENT");
        assertThat(first.path("model").asText()).isEqualTo("models/gemini-embedding-001");
        assertThat(first.path("outputDimensionality").asInt()).isEqualTo(3);
        assertThat(bodies.get(2).path("taskType").asText()).isEqualTo("RETRIEVAL_QUERY");
        assertThat(bodies.get(2).toString()).doesNotContain("test-key");
        assertThat(p.semantic()).isTrue();
        assertThat(p.id()).isEqualTo("gemini:gemini-embedding-001:3");
    }

    @Test
    void gemini_embedding2_putsTaskInTextAndOmitsTaskType() throws Exception {
        List<JsonNode> bodies = new CopyOnWriteArrayList<>();
        String url = startServer(body -> "{\"embedding\":{\"values\":[1.0,0.0]}}", new CopyOnWriteArrayList<>(), bodies,
                new CopyOnWriteArrayList<>(), 200);
        GeminiEmbeddingProvider p = new GeminiEmbeddingProvider("k", url, "gemini-embedding-2", 2);

        p.embedQuery("음성 통화");

        JsonNode body = bodies.get(0);
        assertThat(body.has("taskType")).isFalse();
        assertThat(body.at("/content/parts/0/text").asText()).isEqualTo("task: search result | query: 음성 통화");
    }

    @Test
    void gemini_httpErrorDoesNotLeakKeyOrBody_andMissingKeyFailsBeforeAnyCall() throws Exception {
        String url = startServer(body -> "{\"error\":\"echo test-key-secret " + body + "\"}", new CopyOnWriteArrayList<>(),
                new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>(), 400);
        GeminiEmbeddingProvider p = new GeminiEmbeddingProvider("test-key-secret", url, "gemini-embedding-001", 3);

        assertThatThrownBy(() -> p.embedQuery("비밀 질문")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("400").hasMessageNotContaining("test-key-secret").hasMessageNotContaining("비밀 질문");

        List<String> paths = new CopyOnWriteArrayList<>();
        server.stop(0);
        String url2 = startServer(body -> "{}", paths, new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>(), 200);
        GeminiEmbeddingProvider noKey = new GeminiEmbeddingProvider("", url2, "gemini-embedding-001", 3);
        assertThatThrownBy(() -> noKey.embedQuery("q")).isInstanceOf(IllegalStateException.class);
        assertThat(paths).isEmpty();
    }

    @Test
    void gemini_malformedResponsesAreRejected() throws Exception {
        String url = startServer(body -> "{\"embeddings\":[]}", new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>(),
                new CopyOnWriteArrayList<>(), 200);
        GeminiEmbeddingProvider p = new GeminiEmbeddingProvider("k", url, "gemini-embedding-001", 3);
        assertThatThrownBy(() -> p.embedDocuments(List.of("a"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> p.embedQuery("a")).isInstanceOf(IllegalStateException.class);
    }

    // ---- CachingEmbeddingProvider ----

    @Test
    void cache_reembedsOnlyChangedTexts_andPersistsWithoutStoringText(@TempDir Path dir) throws Exception {
        FakeEmbeddingProvider fake = new FakeEmbeddingProvider();
        CachingEmbeddingProvider cached = new CachingEmbeddingProvider(fake, dir);

        cached.embedDocuments(List.of("음성 통화 문서", "데이터베이스 문서"));
        assertThat(cached.lastStats()).containsExactly(0, 2);
        cached.embedDocuments(List.of("음성 통화 문서", "데이터베이스 문서", "배포 문서"));
        assertThat(cached.lastStats()).containsExactly(2, 1);

        CachingEmbeddingProvider reloaded = new CachingEmbeddingProvider(new FakeEmbeddingProvider(), dir);
        reloaded.embedDocuments(List.of("음성 통화 문서", "배포 문서"));
        assertThat(reloaded.lastStats()).containsExactly(2, 0);

        String stored = java.nio.file.Files.readString(dir.resolve("embeddings-fake-topic-v1.json"));
        assertThat(stored).doesNotContain("음성 통화 문서").doesNotContain("데이터베이스");
    }

    @Test
    void cache_corruptedFileIsDiscarded(@TempDir Path dir) throws Exception {
        java.nio.file.Files.writeString(dir.resolve("embeddings-fake-topic-v1.json"), "{not json");
        CachingEmbeddingProvider cached = new CachingEmbeddingProvider(new FakeEmbeddingProvider(), dir);
        cached.embedDocuments(List.of("음성 통화 문서"));
        assertThat(cached.lastStats()).containsExactly(0, 1);
    }

    @Test
    void cache_doesNotCacheQueries() {
        FakeEmbeddingProvider fake = new FakeEmbeddingProvider();
        CachingEmbeddingProvider cached = new CachingEmbeddingProvider(fake, java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "storia-rag-test-" + System.nanoTime()));
        cached.embedQuery("질문");
        cached.embedQuery("질문");
        assertThat(fake.queryCalls).isEqualTo(2);
    }

    /** startServer 시그니처용 더미 (실제 응답은 컨텍스트 재등록으로 처리). */
    static final class GeminiEmbeddingProviderTestHelper {
        static String unused(JsonNode body) {
            return "{}";
        }
    }
}
