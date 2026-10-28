package com.storia.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 설정 ({@code storia.rag.*}).
 *
 * @param docsRoot 허용 목록 문서를 찾을 저장소 루트 (기본 {@code ../..}: apps/backend에서 실행할 때의 저장소 루트)
 * @param provider {@code local}(기본, 외부 호출 없음) 또는 {@code gemini}(외부 유료 임베딩 호출)
 * @param cacheDir 임베딩 캐시 디렉터리 (gemini일 때만 사용)
 * @param geminiModel 임베딩 모델 이름
 * @param dimensions 임베딩 차원
 * @param minScore 근거 인정 최소 점수 (null이면 임베딩 제공자의 기본값)
 */
@ConfigurationProperties(prefix = "storia.rag")
public record RagProperties(String docsRoot, String provider, String cacheDir, String geminiModel, Integer dimensions,
                            Double minScore) {

    public String docsRootOrDefault() {
        return docsRoot == null || docsRoot.isBlank() ? "../.." : docsRoot;
    }

    public boolean useGemini() {
        return "gemini".equalsIgnoreCase(provider);
    }
}
