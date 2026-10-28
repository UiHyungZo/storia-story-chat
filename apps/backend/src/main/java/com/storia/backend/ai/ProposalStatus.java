package com.storia.backend.ai;

public enum ProposalStatus {
    PENDING, APPROVED, REJECTED,
    /** 제안 이후 캐릭터 설정이 바뀌어 승인이 거부된 제안. */
    STALE
}
