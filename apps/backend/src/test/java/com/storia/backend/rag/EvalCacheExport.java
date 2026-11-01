package com.storia.backend.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 평가 배치 캐시(build/rag-live-cache) → 관리자 RAG 캐시({@link CachingEmbeddingProvider} 형식) 오프라인 변환.
 * 원격 호출 없음. 두 캐시의 벡터 인코딩(float32 big-endian Base64, 제공자가 정규화한 값)은 같고 키만 다르다:
 * 평가 캐시 = SHA-256(요청 형식 식별자 + "\n" + 접두어 붙인 API 입력), 관리자 캐시 = SHA-256(조각 원문), 파일 이름 =
 * 제공자 id. 현재 코퍼스의 조각마다 평가 키로 벡터를 찾아 관리자 키로 옮기므로 <b>지금 문서와 같은 텍스트의 벡터만</b>
 * 옮겨진다. 식별자·차원·벡터 길이·노름이 하나라도 맞지 않으면 아무것도 쓰지 않는다. 기존 관리자 캐시 파일은 덮어쓰지 않는다.
 */
final class EvalCacheExport {

    record Result(String signature, String targetFile, int corpusChunks, int matched, int missing,
                  int unusedEvalEntries, boolean written) {
    }

    private EvalCacheExport() {
    }

    static Result export(GeminiEmbeddingProvider gemini, Path evalCacheDir, Path targetCacheDir, List<String> corpus,
                         boolean write) throws IOException {
        String signature = gemini.documentRequestSignature();
        Path evalFile = evalCacheDir.resolve(
                "eval-batch-cache-" + MarkdownChunker.sha256(signature).substring(0, 16) + ".json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(Files.readString(evalFile, StandardCharsets.UTF_8));
        if (root.path("version").asInt() != 1 || !signature.equals(root.path("signature").asText())
                || root.path("dims").asInt() != gemini.dimensions()) {
            throw new IllegalStateException("평가 캐시 형식·식별자·차원 불일치");
        }
        JsonNode vectors = root.path("vectors");
        Map<String, String> out = new LinkedHashMap<>();
        int missing = 0;
        for (String text : corpus) {
            JsonNode v = vectors.get(MarkdownChunker.sha256(signature + "\n" + gemini.documentText(text)));
            if (v == null) {
                missing++;
                continue;
            }
            checkVector(v.asText(), gemini.dimensions());
            out.put(MarkdownChunker.sha256(text), v.asText());
        }
        Path target = targetCacheDir.resolve(
                "embeddings-" + gemini.id().replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
        boolean written = false;
        if (write) {
            if (Files.exists(target)) {
                throw new IllegalStateException("관리자 캐시 파일이 이미 있어 덮어쓰지 않음: " + target.getFileName());
            }
            Files.createDirectories(targetCacheDir);
            ObjectNode node = mapper.createObjectNode();
            out.forEach(node::put);
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, mapper.writeValueAsString(node), StandardCharsets.UTF_8);
            Files.move(tmp, target);
            written = true;
        }
        return new Result(signature, target.getFileName().toString(), corpus.size(), out.size(), missing,
                vectors.size() - out.size(), written);
    }

    private static void checkVector(String base64, int dims) {
        byte[] bytes = java.util.Base64.getDecoder().decode(base64);
        if (bytes.length != dims * 4) {
            throw new IllegalStateException("벡터 길이 불일치");
        }
        float[] v = new float[dims];
        java.nio.ByteBuffer.wrap(bytes).asFloatBuffer().get(v);
        double norm = 0;
        for (float x : v) {
            norm += (double) x * x;
        }
        if (Math.abs(Math.sqrt(norm) - 1) > 1e-3) {
            throw new IllegalStateException("정규화되지 않은 벡터");
        }
    }
}
