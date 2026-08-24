package com.storia.backend.dto;

/**
 * {@link com.storia.backend.exception.GlobalExceptionHandler}가 내려주는 공통 에러 응답 형태.
 * {@code code}는 클라이언트가 분기 처리할 안정적인 식별자, {@code message}는 사용자에게 보여줄 문구.
 */
public record ErrorResponse(String code, String message) {
}
