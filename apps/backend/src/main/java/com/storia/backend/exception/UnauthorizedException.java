package com.storia.backend.exception;

/**
 * 운영 콘솔 요청에 유효한 관리자 세션이 없을 때 던진다. {@link GlobalExceptionHandler}가 401로 매핑한다.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
