package com.storia.backend.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Gemini Embedding API 기반 의미 임베딩 (외부 유료 호출 — 문서 발췌/질의 텍스트가 Google로 전송된다).
 * 이 클래스는 Spring에 의존하지 않아 MCP 서버 프로세스에서도 그대로 쓴다.
 *
 * <p>모델별 형식(공식 문서 기준): {@code gemini-embedding-2}는 {@code taskType}을 지원하지 않아 텍스트에
 * 접두어({@code "task: search result | query: …"}, {@code "title: … | text: …"})를 붙이고, 그 외 모델
 * ({@code gemini-embedding-001})은 {@code taskType}을 보낸다. 3072차원이 아닌 출력은 직접 정규화한다.
 *
 * <p><b>실제 API 호출로 검증되지 않았다</b> — 요청/응답 파싱은 Mock HTTP 서버 테스트로만 확인했다.
 */
public final class GeminiEmbeddingProvider implements EmbeddingProvider {

    static final int BATCH_SIZE = 50;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int dimensions;
    private final boolean taskInText;

    public GeminiEmbeddingProvider(String apiKey, String baseUrl, String model, int dimensions) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), apiKey, baseUrl, model, dimensions);
    }

    GeminiEmbeddingProvider(HttpClient http, String apiKey, String baseUrl, String model, int dimensions) {
        this.http = http;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.dimensions = dimensions;
        this.taskInText = model.contains("embedding-2");
    }

    @Override
    public String id() {
        return "gemini:" + model + ":" + dimensions;
    }

    @Override
    public boolean semantic() {
        return true;
    }

    @Override
    public double defaultMinScore() {
        return 0.55;
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        List<float[]> all = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            List<String> batch = texts.subList(start, Math.min(texts.size(), start + BATCH_SIZE));
            ObjectNode body = mapper.createObjectNode();
            ArrayNode requests = body.putArray("requests");
            for (String text : batch) {
                requests.add(request(documentText(text), false));
            }
            JsonNode response = post(":batchEmbedContents", body);
            JsonNode embeddings = response.path("embeddings");
            if (!embeddings.isArray() || embeddings.size() != batch.size()) {
                throw new IllegalStateException("임베딩 응답 형식 오류");
            }
            embeddings.forEach(e -> all.add(vector(e.path("values"))));
        }
        return all;
    }

    @Override
    public float[] embedQuery(String text) {
        JsonNode response = post(":embedContent", request(queryText(text), true));
        return vector(response.path("embedding").path("values"));
    }

    private ObjectNode request(String text, boolean query) {
        ObjectNode node = mapper.createObjectNode();
        node.put("model", "models/" + model);
        node.putObject("content").putArray("parts").addObject().put("text", text);
        if (!taskInText) {
            node.put("taskType", query ? "RETRIEVAL_QUERY" : "RETRIEVAL_DOCUMENT");
        }
        node.put("outputDimensionality", dimensions);
        return node;
    }

    /** 실제로 API에 보내는 문서 입력(모델에 따라 접두어 포함). 캐시 키 계산에도 쓴다. */
    String documentText(String text) {
        return taskInText ? "title: none | text: " + text : text;
    }

    private String queryText(String text) {
        return taskInText ? "task: search result | query: " + text : text;
    }

    private JsonNode post(String method, ObjectNode body) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY가 설정되지 않아 임베딩을 만들 수 없습니다.");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1beta/models/" + model + method))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // 응답 본문에는 요청 내용이 반영될 수 있으므로 상태 코드와 형식이 정해진 진단 값만 꺼낸다.
                throw httpFailure(response);
            }
            return mapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("임베딩 호출 중단");
        } catch (IOException e) {
            throw new IllegalStateException("임베딩 호출 실패: " + e.getClass().getSimpleName());
        }
    }

    private EmbeddingHttpException httpFailure(HttpResponse<String> response) {
        String retryAfter = response.headers().firstValue("Retry-After")
                .filter(v -> v.matches("[0-9A-Za-z ,:-]{1,40}")).orElse(null);
        String errorStatus = null;
        String retryDelay = null;
        try {
            JsonNode error = mapper.readTree(response.body()).path("error");
            String s = error.path("status").asText("");
            errorStatus = s.matches("[A-Z_]{1,40}") ? s : null;
            for (JsonNode detail : error.path("details")) {
                String delay = detail.path("retryDelay").asText("");
                if (detail.path("@type").asText("").endsWith("RetryInfo") && delay.matches("\\d{1,6}(\\.\\d{1,9})?s")) {
                    retryDelay = delay;
                }
            }
        } catch (IOException | RuntimeException e) {
            // 본문이 JSON이 아니면 상태 코드만 남긴다.
        }
        return new EmbeddingHttpException(response.statusCode(), retryAfter, errorStatus, retryDelay);
    }

    /**
     * 문서 임베딩 요청의 형식 식별자(모델·차원·호출 방식·작업 지정 방식). 같은 텍스트라도 이 값이 다르면 다른 벡터이므로
     * 캐시 키에 넣는다. 문서 내용은 포함하지 않는다.
     */
    String documentRequestSignature() {
        return "gemini-doc-v1|method=batchEmbedContents|model=models/" + model + "|dims=" + dimensions + "|task="
                + (taskInText ? "text-prefix" : "taskType:RETRIEVAL_DOCUMENT");
    }

    int dimensions() {
        return dimensions;
    }

    private float[] vector(JsonNode values) {
        if (!values.isArray() || values.isEmpty()) {
            throw new IllegalStateException("임베딩 응답에 벡터가 없습니다.");
        }
        float[] v = new float[values.size()];
        double norm = 0;
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) values.get(i).asDouble();
            norm += (double) v[i] * v[i];
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            throw new IllegalStateException("영벡터 임베딩");
        }
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }
}
