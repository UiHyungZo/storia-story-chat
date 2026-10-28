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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * Gemini 실평가 전용 문서 임베딩 캐시 (테스트 소스 — 운영·캐릭터 RAG는 {@link CachingEmbeddingProvider}를 그대로 쓴다).
 *
 * <ul>
 *   <li>문서를 {@link GeminiEmbeddingProvider#BATCH_SIZE}개씩 직접 나눠 배치마다 한 번씩 호출하고, 배치가 성공할 때마다
 *       디스크에 저장한다. 뒤 배치가 실패해도 앞에서 성공한 배치는 재실행 때 다시 호출하지 않는다.</li>
 *   <li>캐시 키 = SHA-256(요청 형식 식별자(모델·차원·호출 방식·작업 지정 방식) + 실제 API 입력 텍스트). 파일 헤더의 형식과
 *       차원이 맞지 않거나 벡터 길이가 다르면 그 항목은 쓰지 않는다. 응답 개수·차원이 맞지 않는 배치는 저장하지 않는다.</li>
 *   <li>실패하면 즉시 중단한다(재시도 없음). 실패 정보는 배치 번호·요청 건수·HTTP 상태·오류 유형·Retry-After 등
 *       안전한 값만 담는다 — 키·요청 본문·문서 내용·오류 메시지 문장은 담지 않는다.</li>
 *   <li>배치 사이 간격({@code intervalMs})은 원격 호출 사이에만 적용한다. 질의 임베딩은 캐시하지 않는다.</li>
 * </ul>
 */
final class EvalBatchEmbeddingCache implements EmbeddingProvider {

    static final int FORMAT_VERSION = 1;

    /** 문서 임베딩 배치 실패 기록. 재실행 시 남은 호출량 추정을 함께 담는다. */
    record BatchFailure(int batchNumber, int totalBatches, int batchSize, Integer httpStatus, String errorType,
                        String errorStatus, String retryAfter, String retryDelay, int succeededBatchesThisRun,
                        int cachedTextsAfterFailure, int remainingTexts, int remainingBatches) {
    }

    static final class BatchAbortException extends IllegalStateException {
        final transient BatchFailure failure;

        BatchAbortException(BatchFailure f) {
            super("문서 임베딩 배치 " + f.batchNumber() + "/" + f.totalBatches() + " 실패 (" + f.errorType()
                    + (f.httpStatus() == null ? "" : ", HTTP " + f.httpStatus())
                    + (f.errorStatus() == null ? "" : ", " + f.errorStatus())
                    + (f.retryAfter() == null ? "" : ", Retry-After=" + f.retryAfter())
                    + (f.retryDelay() == null ? "" : ", retryDelay=" + f.retryDelay())
                    + ") — 이번 실행 성공 " + f.succeededBatchesThisRun() + "배치, 재실행 시 남은 문서 " + f.remainingTexts()
                    + "개(" + f.remainingBatches() + "배치)");
            this.failure = f;
        }
    }

    private final GeminiEmbeddingProvider delegate;
    private final String signature;
    private final Path file;
    private final long intervalMs;
    private final int maxItemsPerBatch;
    private final int maxCharsPerBatch;
    private final EvalRateLimiter limiter;
    private final LongConsumer sleeper;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, float[]> cache = new HashMap<>();
    int remoteBatchCalls;
    int reusedTexts;
    int ignoredCacheEntries;
    BatchFailure lastFailure;

    EvalBatchEmbeddingCache(GeminiEmbeddingProvider delegate, Path cacheDir, long intervalMs) {
        this(delegate, cacheDir, intervalMs, EvalBatchEmbeddingCache::sleep);
    }

    EvalBatchEmbeddingCache(GeminiEmbeddingProvider delegate, Path cacheDir, long intervalMs, LongConsumer sleeper) {
        this(delegate, cacheDir, intervalMs, GeminiEmbeddingProvider.BATCH_SIZE, Integer.MAX_VALUE,
                EvalRateLimiter.unlimited(), sleeper);
    }

    /**
     * @param maxItemsPerBatch 배치당 최대 항목 수 (≤ {@link GeminiEmbeddingProvider#BATCH_SIZE})
     * @param maxCharsPerBatch 배치당 실제 API 입력 글자 수 상한(토큰 추정 상한). 한 조각이 이보다 길면 그 조각만 단독 배치.
     * @param limiter          문서 배치와 질의 모두에 적용하는 분당 항목·토큰 예산
     */
    EvalBatchEmbeddingCache(GeminiEmbeddingProvider delegate, Path cacheDir, long intervalMs, int maxItemsPerBatch,
                            int maxCharsPerBatch, EvalRateLimiter limiter, LongConsumer sleeper) {
        this.delegate = delegate;
        this.signature = delegate.documentRequestSignature();
        this.file = cacheDir.resolve("eval-batch-cache-" + MarkdownChunker.sha256(signature).substring(0, 16) + ".json");
        this.intervalMs = Math.max(0, intervalMs);
        this.maxItemsPerBatch = Math.max(1, Math.min(maxItemsPerBatch, GeminiEmbeddingProvider.BATCH_SIZE));
        this.maxCharsPerBatch = Math.max(1, maxCharsPerBatch);
        this.limiter = limiter;
        this.sleeper = sleeper;
        load();
    }

    /** 아직 캐시에 없는 문서를 실제로 보낼 배치 계획(배치마다 항목 수·입력 글자 수). 원격 호출 없음. */
    List<int[]> plan(List<String> texts) {
        List<int[]> out = new ArrayList<>();
        for (List<Map.Entry<String, String>> batch : batches(missing(texts))) {
            out.add(new int[] {batch.size(), chars(batch)});
        }
        return out;
    }

    private Map<String, String> missing(List<String> texts) {
        Map<String, String> missing = new LinkedHashMap<>(); // key → text (중복 제거, 순서 유지)
        for (String text : texts) {
            String k = key(text);
            if (!cache.containsKey(k)) {
                missing.putIfAbsent(k, text);
            }
        }
        return missing;
    }

    private List<List<Map.Entry<String, String>>> batches(Map<String, String> missing) {
        List<List<Map.Entry<String, String>>> out = new ArrayList<>();
        List<Map.Entry<String, String>> current = new ArrayList<>();
        int currentChars = 0;
        for (Map.Entry<String, String> e : missing.entrySet()) {
            int c = delegate.documentText(e.getValue()).length();
            if (!current.isEmpty() && (current.size() >= maxItemsPerBatch || currentChars + c > maxCharsPerBatch)) {
                out.add(current);
                current = new ArrayList<>();
                currentChars = 0;
            }
            current.add(e);
            currentChars += c;
        }
        if (!current.isEmpty()) {
            out.add(current);
        }
        return out;
    }

    private int chars(List<Map.Entry<String, String>> batch) {
        return batch.stream().mapToInt(e -> delegate.documentText(e.getValue()).length()).sum();
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
    public float[] embedQuery(String text) {
        limiter.acquire(1, delegate.queryText(text).length());
        return delegate.embedQuery(text);
    }

    String key(String text) {
        return MarkdownChunker.sha256(signature + "\n" + delegate.documentText(text));
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        Map<String, String> missing = missing(texts);
        reusedTexts += texts.size() - missing.size();

        List<List<Map.Entry<String, String>>> plan = batches(missing);
        int totalBatches = plan.size();
        int succeeded = 0;
        for (int b = 0; b < totalBatches; b++) {
            List<Map.Entry<String, String>> batch = plan.get(b);
            if (b > 0 && intervalMs > 0) {
                sleeper.accept(intervalMs);
            }
            try {
                limiter.acquire(batch.size(), chars(batch));
            } catch (IllegalStateException e) {
                throw abort(b, plan, succeeded, new OverBudget());
            }
            List<float[]> vectors;
            try {
                remoteBatchCalls++;
                vectors = delegate.embedDocuments(batch.stream().map(Map.Entry::getValue).toList());
            } catch (RuntimeException e) {
                throw abort(b, plan, succeeded, e);
            }
            if (vectors.size() != batch.size()
                    || vectors.stream().anyMatch(v -> v == null || v.length != delegate.dimensions())) {
                throw abort(b, plan, succeeded, null);
            }
            for (int i = 0; i < batch.size(); i++) {
                cache.put(batch.get(i).getKey(), vectors.get(i));
            }
            try {
                save();
            } catch (IOException e) {
                // 저장하지 못하면 다음 배치를 진행해도 재실행 때 다시 과금되므로 여기서 멈춘다.
                cache.keySet().removeAll(batch.stream().map(Map.Entry::getKey).toList());
                throw abort(b, plan, succeeded, new CacheWriteFailed());
            }
            succeeded++;
        }

        List<float[]> out = new ArrayList<>(texts.size());
        for (String text : texts) {
            out.add(cache.get(key(text)));
        }
        return out;
    }

    private static final class CacheWriteFailed extends RuntimeException {
    }

    private static final class OverBudget extends RuntimeException {
    }

    private BatchAbortException abort(int batchIndex, List<List<Map.Entry<String, String>>> plan, int succeeded,
                                      RuntimeException cause) {
        int totalBatches = plan.size();
        int batchSize = plan.get(batchIndex).size();
        int remainingTexts = 0;
        for (int i = succeeded; i < plan.size(); i++) {
            remainingTexts += plan.get(i).size();
        }
        Integer status = null;
        String errorStatus = null;
        String retryAfter = null;
        String retryDelay = null;
        String type;
        if (cause instanceof EmbeddingHttpException h) {
            type = h.status() == 429 ? "rate_limited" : "http_error";
            status = h.status();
            errorStatus = h.errorStatus();
            retryAfter = h.retryAfter();
            retryDelay = h.retryDelay();
        } else if (cause == null) {
            type = "invalid_response";
        } else if (cause instanceof CacheWriteFailed) {
            type = "cache_write_failed";
        } else if (cause instanceof OverBudget) {
            type = "request_over_budget";
        } else {
            type = "client_error:" + cause.getClass().getSimpleName();
        }
        // 재실행 시에는 남은 조각을 같은 규칙으로 다시 묶으므로 배치 수는 남은 계획 배치 수와 같다.
        BatchFailure f = new BatchFailure(batchIndex + 1, totalBatches, batchSize, status, type, errorStatus, retryAfter,
                retryDelay, succeeded, cache.size(), remainingTexts, totalBatches - succeeded);
        lastFailure = f;
        return new BatchAbortException(f);
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
            if (root.path("version").asInt() != FORMAT_VERSION || !signature.equals(root.path("signature").asText())
                    || root.path("dims").asInt() != delegate.dimensions()) {
                ignoredCacheEntries = root.path("vectors").size();
                return;
            }
            root.path("vectors").fields().forEachRemaining(e -> {
                byte[] bytes = Base64.getDecoder().decode(e.getValue().asText());
                if (bytes.length != delegate.dimensions() * 4) {
                    ignoredCacheEntries++;
                    return;
                }
                float[] v = new float[delegate.dimensions()];
                ByteBuffer.wrap(bytes).asFloatBuffer().get(v);
                cache.put(e.getKey(), v);
            });
        } catch (IOException | RuntimeException e) {
            cache.clear(); // 손상된 파일은 쓰지 않는다(다음 성공 배치 저장 시 덮어쓴다).
        }
    }

    private void save() throws IOException {
        Files.createDirectories(file.getParent());
        ObjectNode root = mapper.createObjectNode();
        root.put("version", FORMAT_VERSION);
        root.put("signature", signature);
        root.put("dims", delegate.dimensions());
        ObjectNode vectors = root.putObject("vectors");
        cache.forEach((k, v) -> {
            ByteBuffer buffer = ByteBuffer.allocate(v.length * 4);
            buffer.asFloatBuffer().put(v);
            vectors.put(k, Base64.getEncoder().encodeToString(buffer.array()));
        });
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, mapper.writeValueAsString(root), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    Path file() {
        return file;
    }

    int cachedTexts() {
        return cache.size();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("배치 간격 대기 중단");
        }
    }
}
