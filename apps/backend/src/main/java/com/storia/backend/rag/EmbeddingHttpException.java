package com.storia.backend.rag;

/**
 * 임베딩 API의 비정상 HTTP 응답. 메시지는 기존과 같은 "임베딩 호출 실패: HTTP {status}"이고, 응답에서 안전하게 꺼낼 수
 * 있는 진단 정보만 담는다: 상태 코드, {@code Retry-After} 헤더, 오류 본문의 {@code error.status} 열거값
 * (예: RESOURCE_EXHAUSTED), {@code RetryInfo.retryDelay}(예: "30s"). 오류 메시지 문장·요청 본문·문서 내용·키는 담지 않는다.
 */
public final class EmbeddingHttpException extends IllegalStateException {

    private final int status;
    private final String retryAfter;
    private final String errorStatus;
    private final String retryDelay;

    public EmbeddingHttpException(int status, String retryAfter, String errorStatus, String retryDelay) {
        super("임베딩 호출 실패: HTTP " + status);
        this.status = status;
        this.retryAfter = retryAfter;
        this.errorStatus = errorStatus;
        this.retryDelay = retryDelay;
    }

    public int status() {
        return status;
    }

    /** {@code Retry-After} 헤더 값(초 또는 HTTP 날짜). 없으면 null. */
    public String retryAfter() {
        return retryAfter;
    }

    /** 오류 본문의 {@code error.status} (대문자 열거값만). 없거나 형식이 다르면 null. */
    public String errorStatus() {
        return errorStatus;
    }

    /** 오류 본문 {@code error.details[]}의 RetryInfo {@code retryDelay}(예: "30s"). 없으면 null. */
    public String retryDelay() {
        return retryDelay;
    }
}
