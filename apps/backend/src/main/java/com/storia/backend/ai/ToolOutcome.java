package com.storia.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;

/** 도구 실행 결과. 실패해도 예외 대신 ok=false + 오류 payload로 모델에 돌려준다. */
public record ToolOutcome(boolean ok, JsonNode payload) {
}
