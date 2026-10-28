package com.storia.backend.rag;

import java.util.List;

/**
 * 텍스트 → 벡터 변환 포트. 구현체는 교체 가능하다:
 * <ul>
 *   <li>{@link LocalHashingEmbeddingProvider} — 외부 호출 없는 로컬 n-gram 해싱 벡터. <b>의미(semantic) 임베딩이 아니다</b>.</li>
 *   <li>{@link GeminiEmbeddingProvider} — Gemini Embedding API (외부 유료 호출, 의미 임베딩).</li>
 *   <li>테스트용 Fake(테스트 소스) — 결정적 가짜 벡터.</li>
 * </ul>
 * 반환 벡터는 L2 정규화되어 있어야 한다 (코사인 유사도 = 내적).
 */
public interface EmbeddingProvider {

    /** 인덱스 정보와 캐시 키에 쓰는 식별자. */
    String id();

    /** 의미 기반 임베딩이면 true. 어휘 중첩 기반이면 false. */
    boolean semantic();

    /** 이 임베딩에서 "근거 있음"으로 인정하는 최소 코사인 점수의 기본값. */
    double defaultMinScore();

    /** 인덱싱 직전에 전체 코퍼스를 알려준다 (IDF 계산 등). 기본은 아무것도 하지 않는다. */
    default void prepare(List<String> corpus) {
    }

    List<float[]> embedDocuments(List<String> texts);

    float[] embedQuery(String text);
}
