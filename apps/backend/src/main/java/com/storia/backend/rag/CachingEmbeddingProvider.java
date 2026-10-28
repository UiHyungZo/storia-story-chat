package com.storia.backend.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 문서 임베딩 디스크 캐시 데코레이터. 키는 (provider id + 텍스트 SHA-256)이라 문서가 바뀌면 바뀐 조각만 다시
 * 임베딩한다 — 유료 임베딩 API의 재인덱싱 비용을 줄이기 위함. 캐시에는 해시와 벡터만 저장하며 문서 원문은 저장하지
 * 않는다. 질의 임베딩은 캐시하지 않는다.
 */
public final class CachingEmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingProvider delegate;
    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, float[]> cache = new HashMap<>();
    private int lastHits;
    private int lastMisses;

    public CachingEmbeddingProvider(EmbeddingProvider delegate, Path cacheDir) {
        this.delegate = delegate;
        this.file = cacheDir.resolve("embeddings-" + delegate.id().replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
        load();
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public boolean semantic() {
        return delegate.semantic();
    }

    @Override
    public double defaultMinScore() {
        return delegate.defaultMinScore();
    }

    @Override
    public void prepare(List<String> corpus) {
        delegate.prepare(corpus);
    }

    @Override
    public synchronized List<float[]> embedDocuments(List<String> texts) {
        List<float[]> result = new ArrayList<>(texts.size());
        List<Integer> missIndexes = new ArrayList<>();
        List<String> missTexts = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            float[] cached = cache.get(MarkdownChunker.sha256(texts.get(i)));
            result.add(cached);
            if (cached == null) {
                missIndexes.add(i);
                missTexts.add(texts.get(i));
            }
        }
        lastHits = texts.size() - missTexts.size();
        lastMisses = missTexts.size();
        if (!missTexts.isEmpty()) {
            List<float[]> fresh = delegate.embedDocuments(missTexts);
            for (int j = 0; j < missIndexes.size(); j++) {
                result.set(missIndexes.get(j), fresh.get(j));
                cache.put(MarkdownChunker.sha256(missTexts.get(j)), fresh.get(j));
            }
            save();
        }
        return result;
    }

    @Override
    public float[] embedQuery(String text) {
        return delegate.embedQuery(text);
    }

    /** 마지막 embedDocuments 호출의 캐시 적중/미스 수 (테스트·로그용). */
    public synchronized int[] lastStats() {
        return new int[] {lastHits, lastMisses};
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
            root.fields().forEachRemaining(e -> {
                byte[] bytes = Base64.getDecoder().decode(e.getValue().asText());
                float[] v = new float[bytes.length / 4];
                ByteBuffer.wrap(bytes).asFloatBuffer().get(v);
                cache.put(e.getKey(), v);
            });
        } catch (IOException | RuntimeException e) {
            cache.clear(); // 손상된 캐시는 버리고 다시 만든다.
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            ObjectNode root = mapper.createObjectNode();
            cache.forEach((k, v) -> {
                ByteBuffer buffer = ByteBuffer.allocate(v.length * 4);
                buffer.asFloatBuffer().put(v);
                root.put(k, Base64.getEncoder().encodeToString(buffer.array()));
            });
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, mapper.writeValueAsString(root), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // 캐시 저장 실패는 검색 동작에 영향이 없다.
        }
    }
}
