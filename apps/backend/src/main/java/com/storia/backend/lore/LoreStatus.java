package com.storia.backend.lore;

/** 캐릭터 세계관 검색 결과 상태. OK 외에는 대화에 세계관을 넣지 않고 기존 System Prompt만 쓴다. */
public enum LoreStatus {
    /** 근거를 찾았다. */
    OK,
    /** 세계관 문서는 있으나 질문과 관련된 근거가 없다. */
    NO_EVIDENCE,
    /** 기능 플래그가 꺼져 있다. */
    DISABLED,
    /** 이 캐릭터에 연결된 세계관 문서가 없다(매핑 없음/문서 없음/인덱스 비어 있음). */
    NO_LORE,
    /** 시간 예산을 넘겼다. */
    TIMEOUT,
    /** 질의가 유효하지 않다. */
    INVALID_INPUT,
    /** 검색 중 오류. */
    ERROR
}
