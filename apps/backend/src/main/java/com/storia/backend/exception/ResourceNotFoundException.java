package com.storia.backend.exception;

/**
 * 요청한 리소스(캐릭터, 대화 턴 등)가 존재하지 않을 때 던진다. 컨트롤러 계층에서
 * {@link GlobalExceptionHandler}가 404로 매핑한다.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
