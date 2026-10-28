package com.storia.backend.dto;

import java.time.Instant;
import java.util.List;

/** 현재 값과 제안 값을 함께 내려 관리자 콘솔이 Diff를 그릴 수 있게 한다. */
public record AdminProposalResponse(
        String id,
        Long characterId,
        String characterName,
        String status,
        String reason,
        Instant createdAt,
        Instant decidedAt,
        /** 제안 이후 현재 설정이 바뀌어 더 이상 적용할 수 없으면 true. */
        boolean stale,
        List<String> changedFields,
        Fields current,
        Fields proposed) {

    public record Fields(String concept, String systemPrompt, String ttsVoiceId) {
    }
}
