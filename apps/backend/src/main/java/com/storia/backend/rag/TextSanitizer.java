package com.storia.backend.rag;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 인덱싱 전 문서 정제. HTML 주석/태그와 이미지 문법을 정리하고, 비밀값처럼 보이는 문자열은 {@code [REDACTED]}로
 * 치환해 인덱스에 들어가지 않게 한다. 지시문처럼 보이는 문장은 제거하지 않는다 — 문서 내용은 항상 "데이터"로
 * 취급되며 실행되지 않는다 (검색 결과 소비 측이 보장).
 */
public final class TextSanitizer {

    public record Result(String text, int redactions) {
    }

    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern HTML_BLOCK = Pattern.compile("<(script|style)\\b.*?</\\1>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG = Pattern.compile(
            "</?(?:a|img|br|p|div|span|details|summary|table|thead|tbody|tr|td|th|center|sup|sub|kbd|b|i|em|strong)\\b[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern BLANKS = Pattern.compile("\\n{3,}");

    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----", Pattern.DOTALL);
    private static final Pattern[] TOKEN_PATTERNS = {
            Pattern.compile("AIza[0-9A-Za-z_\\-]{30,}"),
            Pattern.compile("Bearer\\s+[A-Za-z0-9._\\-]{20,}"),
            Pattern.compile("eyJ[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,}"),
            Pattern.compile("gh[pousr]_[A-Za-z0-9]{30,}"),
            Pattern.compile("sk-[A-Za-z0-9]{20,}"),
            Pattern.compile("xox[baprs]-[A-Za-z0-9-]{10,}"),
    };
    /**
     * KEY=value / key: "value" 형태. 값이 토큰처럼 보일 때만(16자 이상, 영문+숫자 혼합, 경로/호출식 아님) 가린다.
     * 플레이스홀더(your-…, <…>, $VAR, example 등)와 일반 경로·함수명은 그대로 둔다.
     */
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "(?i)((?:api[_-]?key|secret|token|password|passwd|credential)[A-Za-z0-9_]*\\s*[:=]\\s*['\"]?)"
                    + "((?=[A-Za-z0-9_\\-+=.]*\\d)(?=[A-Za-z0-9_\\-+=.]*[A-Za-z])[A-Za-z0-9_\\-+=.]{16,})");
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "(?i)^(your|<|\\$|\\{|\\.\\.\\.|xxx|example|changeme|placeholder|\\*{3,}|\\[REDACTED])");

    private TextSanitizer() {
    }

    public static Result clean(String raw) {
        String text = raw == null ? "" : raw.replace("\r\n", "\n").replace('\r', '\n');
        text = HTML_COMMENT.matcher(text).replaceAll("");
        text = HTML_BLOCK.matcher(text).replaceAll("");
        text = IMAGE.matcher(text).replaceAll("$1");
        text = HTML_TAG.matcher(text).replaceAll("");

        int redactions = 0;
        Matcher key = PRIVATE_KEY.matcher(text);
        while (key.find()) {
            redactions++;
        }
        text = key.reset().replaceAll("[REDACTED]");
        for (Pattern pattern : TOKEN_PATTERNS) {
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                redactions++;
            }
            text = m.reset().replaceAll("[REDACTED]");
        }
        Matcher assignment = ASSIGNMENT.matcher(text);
        StringBuilder out = new StringBuilder();
        while (assignment.find()) {
            if (PLACEHOLDER.matcher(assignment.group(2)).find()) {
                assignment.appendReplacement(out, Matcher.quoteReplacement(assignment.group()));
            } else {
                redactions++;
                assignment.appendReplacement(out, Matcher.quoteReplacement(assignment.group(1) + "[REDACTED]"));
            }
        }
        assignment.appendTail(out);
        text = BLANKS.matcher(out).replaceAll("\n\n").strip();
        return new Result(text, redactions);
    }
}
