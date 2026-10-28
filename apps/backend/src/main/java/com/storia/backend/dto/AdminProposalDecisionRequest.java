package com.storia.backend.dto;

/** 승인 요청 본문. {@code confirm}이 명시적으로 true여야 승인된다. */
public record AdminProposalDecisionRequest(Boolean confirm) {
}
