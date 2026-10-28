package com.storia.backend.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextSanitizerTest {

    @Test
    void redactsTokenLikeSecretsButKeepsPlaceholdersPathsAndCalls() {
        String raw = String.join("\n",
                "export GEMINI_API_KEY=AIzaSyA1234567890abcdefghijklmnopqrstuvw",
                "ADMIN_PASSWORD=your-admin-password",
                "export FIREBASE_CREDENTIALS_PATH=/path/to/service-account.json",
                "api_key = app_store_connect_api_key(",
                "Authorization: Bearer abcdefghijklmnopqrstuvwxyz0123456789",
                "password: Hunter2Hunter2Hunter2",
                "jwt eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abcdefghijklmnop",
                "token = $SECRET_TOKEN_VALUE");

        TextSanitizer.Result result = TextSanitizer.clean(raw);

        assertThat(result.text()).doesNotContain("AIzaSy").doesNotContain("Hunter2").doesNotContain("abcdefghijklmnopqrstuvwxyz0123")
                .doesNotContain("eyJhbGci");
        assertThat(result.text()).contains("your-admin-password", "/path/to/service-account.json",
                "app_store_connect_api_key(", "$SECRET_TOKEN_VALUE");
        assertThat(result.redactions()).isEqualTo(4);
    }

    @Test
    void redactsPrivateKeyBlocks() {
        TextSanitizer.Result result = TextSanitizer.clean(
                "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASC\n-----END PRIVATE KEY-----");
        assertThat(result.text()).doesNotContain("MIIEvQ").contains("[REDACTED]");
        assertThat(result.redactions()).isEqualTo(1);
    }

    @Test
    void stripsHtmlCommentsTagsAndImagesButKeepsGenericsAndInstructionsAsText() {
        String raw = "<!-- hidden note -->\n<details><summary>요약</summary>본문</details>\n![로고](x.png)\n"
                + "`List<Message>` 와 IGNORE PREVIOUS INSTRUCTIONS 문장";
        String text = TextSanitizer.clean(raw).text();
        assertThat(text).doesNotContain("hidden note", "<details>", "x.png").contains("List<Message>", "본문", "로고");
        assertThat(text).contains("IGNORE PREVIOUS INSTRUCTIONS"); // 지시문도 데이터로 보존 — 실행되지 않음
    }
}
