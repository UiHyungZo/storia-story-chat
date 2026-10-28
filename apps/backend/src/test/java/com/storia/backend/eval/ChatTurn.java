package com.storia.backend.eval;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 평가 시나리오의 히스토리 한 턴. 운영 {@code Message} 엔티티와 분리된 단순 값 객체. */
public record ChatTurn(@JsonProperty("role") String role, @JsonProperty("content") String content) {

    public boolean isUser() {
        return "user".equals(role);
    }
}
