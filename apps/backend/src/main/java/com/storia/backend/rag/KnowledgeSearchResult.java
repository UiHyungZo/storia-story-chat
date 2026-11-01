package com.storia.backend.rag;

import java.util.List;

/**
 * 검색 결과. {@code status}가 NO_EVIDENCE이면 근거 문서가 없다는 뜻이며 results는 비어 있다.
 * 질의 원문은 결과에 되돌려주지 않는다.
 */
public record KnowledgeSearchResult(Status status, String message, List<Hit> results, IndexInfo index, long tookMs) {

    public enum Status {
        OK, NO_EVIDENCE, INVALID_INPUT, INDEX_EMPTY, ERROR
    }

    public record Hit(int rank, String chunkId, String docId, String source, String title, String heading, String text,
                      double score) {
    }

    public record IndexInfo(String provider, boolean semantic, int documents, int chunks, String builtAt,
                            List<String> missingSources) {
    }
}
