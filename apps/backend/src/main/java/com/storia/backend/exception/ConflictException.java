package com.storia.backend.exception;

/** 요청이 리소스의 현재 상태와 충돌할 때(이미 처리된 제안, 오래된 제안 등) 던진다. 409로 매핑된다. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
