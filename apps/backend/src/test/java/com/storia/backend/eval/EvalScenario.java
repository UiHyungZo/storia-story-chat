package com.storia.backend.eval;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 합성 평가 시나리오. 필수 필드는 로드맵 Phase 1 정의(id, system_prompt, history, input,
 * expected_behavior, rubric, tags)를 따르고, 기계 검증용 {@code checks}와 판정 방식
 * {@code review}("mechanical" | "human")를 추가한다.
 */
public record EvalScenario(
        @JsonProperty("id") String id,
        @JsonProperty("system_prompt") String systemPrompt,
        @JsonProperty("history") List<ChatTurn> history,
        @JsonProperty("input") String input,
        @JsonProperty("expected_behavior") String expectedBehavior,
        @JsonProperty("rubric") List<String> rubric,
        @JsonProperty("tags") List<String> tags,
        @JsonProperty("review") String review,
        @JsonProperty("checks") List<Check> checks) {
}
