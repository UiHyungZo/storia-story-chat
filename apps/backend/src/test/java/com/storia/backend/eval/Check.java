package com.storia.backend.eval;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 기계적으로 검증 가능한 조건 하나. type: not_empty | contains_any | not_contains_any |
 * max_chars | min_chars | has_hangul.
 */
public record Check(
        @JsonProperty("type") String type,
        @JsonProperty("values") List<String> values,
        @JsonProperty("limit") Integer limit) {
}
