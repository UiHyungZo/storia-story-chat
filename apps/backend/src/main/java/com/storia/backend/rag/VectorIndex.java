package com.storia.backend.rag;

import java.util.List;

/** 벡터 인덱스 포트. 현재 구현은 {@link InMemoryVectorIndex}(전수 코사인 검색). */
public interface VectorIndex {

    record Scored(KnowledgeChunk chunk, double score) {
    }

    int size();

    /** 점수 내림차순 상위 k개. 질의 벡터는 L2 정규화되어 있다고 가정한다. */
    List<Scored> search(float[] query, int k);
}
