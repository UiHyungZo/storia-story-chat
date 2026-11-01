package com.storia.backend.eval;

import java.util.List;
import java.util.regex.Pattern;

/** {@link Check}를 응답 텍스트에 적용한다. 알 수 없는 type은 통과가 아니라 실패로 취급한다. */
final class ScenarioChecks {

    private static final Pattern HANGUL = Pattern.compile("[\\uAC00-\\uD7A3]");

    private ScenarioChecks() {
    }

    /** 통과하면 null, 실패하면 사유 문자열. */
    static String evaluate(Check check, String reply) {
        String text = reply == null ? "" : reply;
        List<String> values = check.values() == null ? List.of() : check.values();
        int chars = text.codePointCount(0, text.length());
        return switch (check.type()) {
            case "not_empty" -> text.isBlank() ? "not_empty: 응답이 비어 있음" : null;
            case "contains_any" -> values.stream().anyMatch(text::contains)
                    ? null : "contains_any: " + values + " 중 어느 것도 없음";
            case "not_contains_any" -> values.stream().filter(text::contains).findFirst()
                    .map(v -> "not_contains_any: 금지 표현 포함 '" + v + "'").orElse(null);
            case "max_chars" -> chars > check.limit()
                    ? "max_chars: " + chars + " > " + check.limit() : null;
            case "min_chars" -> chars < check.limit()
                    ? "min_chars: " + chars + " < " + check.limit() : null;
            case "has_hangul" -> HANGUL.matcher(text).find() ? null : "has_hangul: 한글 없음";
            default -> "unknown check type: " + check.type();
        };
    }
}
