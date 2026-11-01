package com.storia.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 캐릭터 세계관 RAG 설정 ({@code storia.character-rag.*}). 실제 사용자 채팅에 영향을 주므로 기본은 꺼져 있다.
 *
 * @param enabled 기능 플래그 (기본 false)
 * @param provider {@code local}(기본, 외부 호출 없음·어휘 기반) 또는 {@code gemini}(외부 유료 임베딩 호출).
 *                 관리자 문서 RAG({@code storia.rag.provider})와 독립적으로 선택한다.
 * @param timeoutMs 대화 1턴당 검색 시간 예산 (기본 800ms). 넘기면 세계관 없이 응답한다.
 * @param topK 프롬프트에 넣을 최대 근거 수 (기본 3)
 * @param minScore 근거 인정 최소 점수 (null이면 임베딩 제공자 기본값)
 */
@ConfigurationProperties(prefix = "storia.character-rag")
public record CharacterRagProperties(Boolean enabled, String provider, Long timeoutMs, Integer topK, Double minScore) {

    /**
     * 로컬 해싱 제공자용 근거 인정 점수. 합성 질문의 calibration 분할로만 골랐다(0.12 기본값은 세계관 문서의 짧은 질문에
     * 너무 엄격했다). Gemini 제공자는 아직 보정하지 않았으므로 null(제공자 기본값, 미검증 자리표시)을 돌려준다.
     */
    public static final double LOCAL_MIN_SCORE = 0.08;

    public Double effectiveMinScore() {
        if (minScore != null) {
            return minScore;
        }
        return useGemini() ? null : LOCAL_MIN_SCORE;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public boolean useGemini() {
        return "gemini".equalsIgnoreCase(provider);
    }

    public long timeoutMsOrDefault() {
        return timeoutMs == null || timeoutMs < 1 ? 800 : timeoutMs;
    }

    public int topKOrDefault() {
        return topK == null || topK < 1 || topK > 5 ? 3 : topK;
    }
}
