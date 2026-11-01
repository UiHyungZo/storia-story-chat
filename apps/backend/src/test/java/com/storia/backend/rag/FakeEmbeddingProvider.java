package com.storia.backend.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 테스트 전용 가짜 임베딩 (실제 임베딩이 아님). 주제 키워드 → 고정 차원 매핑으로 결정적 벡터를 만든다.
 * 같은 주제 키워드를 가진 텍스트끼리만 유사도가 높아져서, 인덱싱·검색 파이프라인 동작을 외부 호출 없이 검증할 수 있다.
 */
class FakeEmbeddingProvider implements EmbeddingProvider {

    static final Map<String, Integer> TOPICS = Map.of("음성", 0, "통화", 0, "데이터베이스", 1, "mariadb", 1, "배포", 2,
            "testflight", 2, "캐릭터", 3, "인증", 4, "비밀번호", 4);

    int documentCalls;
    int queryCalls;
    boolean failOnQuery;

    @Override
    public String id() {
        return "fake-topic-v1";
    }

    @Override
    public boolean semantic() {
        return false;
    }

    @Override
    public double defaultMinScore() {
        return 0.5;
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        documentCalls++;
        List<float[]> out = new ArrayList<>();
        texts.forEach(t -> out.add(vector(t)));
        return out;
    }

    @Override
    public float[] embedQuery(String text) {
        queryCalls++;
        if (failOnQuery) {
            throw new IllegalStateException("boom secret-host");
        }
        return vector(text);
    }

    private float[] vector(String text) {
        float[] v = new float[6];
        String lower = text.toLowerCase();
        TOPICS.forEach((keyword, dim) -> {
            if (lower.contains(keyword)) {
                v[dim] += 1f;
            }
        });
        v[5] = 0.01f; // 영벡터 방지
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        norm = Math.sqrt(norm);
        for (int i = 0; i < v.length; i++) {
            v[i] /= (float) norm;
        }
        return v;
    }
}
