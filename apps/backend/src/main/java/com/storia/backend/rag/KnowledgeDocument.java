package com.storia.backend.rag;

/** 로딩·정제가 끝난 문서. {@code text}는 {@link TextSanitizer} 처리 후의 내용이다. */
public record KnowledgeDocument(String id, String source, String title, String text, int redactions) {
}
