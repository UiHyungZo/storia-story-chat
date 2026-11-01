package com.storia.backend.rag;

/**
 * {@link KnowledgeBase}가 문서를 읽어오는 포트. 파일 시스템 기반 {@link DocumentLoader}(관리자 문서 RAG)와
 * 클래스패스 기반 {@link ClasspathDocumentLoader}(캐릭터 세계관 RAG)가 구현한다.
 */
public interface KnowledgeDocumentLoader {

    DocumentLoader.Loaded load();

    /** 변경 감지용 값. 값이 달라지면 인덱스를 다시 만든다. */
    String fingerprint();
}
