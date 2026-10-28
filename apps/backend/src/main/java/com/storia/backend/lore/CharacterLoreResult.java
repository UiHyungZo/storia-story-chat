package com.storia.backend.lore;

import com.storia.backend.rag.KnowledgeSearchResult;
import java.util.List;

/** 캐릭터 세계관 검색 결과. 질의 원문은 담지 않는다. hits의 docId는 항상 loreKey와 같다. */
public record CharacterLoreResult(LoreStatus status, String loreKey, List<KnowledgeSearchResult.Hit> hits,
                                  long tookMicros) {

    public static CharacterLoreResult of(LoreStatus status, String loreKey, long tookMicros) {
        return new CharacterLoreResult(status, loreKey, List.of(), tookMicros);
    }
}
