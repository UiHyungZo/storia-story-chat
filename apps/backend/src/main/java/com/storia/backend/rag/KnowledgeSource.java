package com.storia.backend.rag;

/** 검색 대상으로 허용된 문서 하나. 목록에 없는 파일은 절대 읽거나 인덱싱하지 않는다. */
public record KnowledgeSource(String id, String path, String title) {
}
