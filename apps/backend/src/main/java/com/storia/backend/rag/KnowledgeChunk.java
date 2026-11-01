package com.storia.backend.rag;

/**
 * 검색 단위. {@code text}는 사용자에게 보여주는 발췌, {@code embeddingText}는 제목/헤딩 경로를 앞에 붙여
 * 임베딩에 넣는 문자열이다. {@code contentHash}는 embeddingText의 SHA-256이며 임베딩 캐시 키로 쓴다.
 */
public record KnowledgeChunk(
        String chunkId,
        String docId,
        String source,
        String title,
        String heading,
        String text,
        String embeddingText,
        String contentHash) {
}
