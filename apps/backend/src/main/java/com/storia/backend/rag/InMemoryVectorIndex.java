package com.storia.backend.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 불변 인메모리 인덱스. 문서가 수십 개·조각이 수백 개 수준이라 ANN 인덱스나 별도 Vector DB 서버 없이
 * 전수 내적(O(N·d))으로 충분하다. 교체/갱신은 새 인스턴스를 만들어 통째로 바꾼다.
 */
public final class InMemoryVectorIndex implements VectorIndex {

    private final List<KnowledgeChunk> chunks;
    private final List<float[]> vectors;

    public InMemoryVectorIndex(List<KnowledgeChunk> chunks, List<float[]> vectors) {
        if (chunks.size() != vectors.size()) {
            throw new IllegalArgumentException("chunks와 vectors의 크기가 다릅니다.");
        }
        this.chunks = List.copyOf(chunks);
        this.vectors = List.copyOf(vectors);
    }

    @Override
    public int size() {
        return chunks.size();
    }

    @Override
    public List<Scored> search(float[] query, int k) {
        List<Scored> scored = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            float[] v = vectors.get(i);
            if (v.length != query.length) {
                throw new IllegalStateException("벡터 차원 불일치");
            }
            double dot = 0;
            for (int j = 0; j < v.length; j++) {
                dot += (double) v[j] * query[j];
            }
            scored.add(new Scored(chunks.get(i), dot));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        return scored.subList(0, Math.min(k, scored.size()));
    }
}
