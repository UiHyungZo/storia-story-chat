package com.storia.backend.rag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 외부 호출 없는 로컬 벡터화: 단어 + 문자 2·3-gram을 부호 있는 해싱으로 {@value #DIMENSIONS}차원 벡터에
 * 투영하고 IDF 가중·L2 정규화한다. 비용이 없고 결정적이라 오프라인 검색과 평가에 쓸 수 있다.
 *
 * <p><b>한계:</b> 의미를 이해하지 못한다. 어휘(문자열) 중첩이 있어야 유사도가 올라가므로 동의어·패러프레이즈
 * 질문은 놓친다. "의미 기반 RAG"가 필요하면 {@link GeminiEmbeddingProvider}를 쓴다.
 */
public class LocalHashingEmbeddingProvider implements EmbeddingProvider {

    public static final String ID = "local-hashing-v1";
    static final int DIMENSIONS = 1024;

    private volatile double[] idf = new double[DIMENSIONS];
    private volatile double maxIdf = 1.0;

    public LocalHashingEmbeddingProvider() {
        java.util.Arrays.fill(idf, 1.0);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean semantic() {
        return false;
    }

    @Override
    public double defaultMinScore() {
        return 0.12;
    }

    @Override
    public void prepare(List<String> corpus) {
        int[] df = new int[DIMENSIONS];
        for (String text : corpus) {
            boolean[] seen = new boolean[DIMENSIONS];
            for (int bucket : buckets(text)) {
                seen[bucket] = true;
            }
            for (int i = 0; i < DIMENSIONS; i++) {
                if (seen[i]) {
                    df[i]++;
                }
            }
        }
        int n = Math.max(1, corpus.size());
        double[] next = new double[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            next[i] = Math.log((n + 1.0) / (df[i] + 1.0)) + 1.0;
        }
        this.idf = next;
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        texts.forEach(t -> out.add(embed(t)));
        return out;
    }

    @Override
    public float[] embedQuery(String text) {
        return embed(text);
    }

    private float[] embed(String text) {
        double[] v = new double[DIMENSIONS];
        double[] weights = idf;
        for (int signedBucket : signedFeatures(text)) {
            int bucket = Math.abs(signedBucket) - 1;
            double sign = signedBucket > 0 ? 1.0 : -1.0;
            v[bucket] += sign * weights[bucket];
        }
        double norm = 0;
        for (double x : v) {
            norm += x * x;
        }
        norm = Math.sqrt(norm);
        float[] result = new float[DIMENSIONS];
        if (norm > 0) {
            for (int i = 0; i < DIMENSIONS; i++) {
                result[i] = (float) (v[i] / norm);
            }
        }
        return result;
    }

    private List<Integer> buckets(String text) {
        List<Integer> list = new ArrayList<>();
        for (int signed : signedFeatures(text)) {
            list.add(Math.abs(signed) - 1);
        }
        return list;
    }

    /** 부호 있는 버킷 번호(1-based, 부호가 해시 부호)의 목록. */
    private List<Integer> signedFeatures(String text) {
        List<Integer> features = new ArrayList<>();
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String word : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (word.isEmpty()) {
                continue;
            }
            add(features, "w:" + word);
            String padded = "^" + word + "$";
            for (int n = 2; n <= 3; n++) {
                for (int i = 0; i + n <= padded.length(); i++) {
                    add(features, "g:" + padded.substring(i, i + n));
                }
            }
        }
        return features;
    }

    private void add(List<Integer> features, String feature) {
        long h = fnv1a64(feature);
        int bucket = (int) Long.remainderUnsigned(h, DIMENSIONS);
        boolean positive = ((h >>> 63) & 1L) == 0;
        features.add(positive ? bucket + 1 : -(bucket + 1));
    }

    private static long fnv1a64(String s) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
