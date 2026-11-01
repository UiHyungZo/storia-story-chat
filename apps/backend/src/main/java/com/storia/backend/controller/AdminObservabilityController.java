package com.storia.backend.controller;

import com.storia.backend.ai.AgentTelemetryStore;
import com.storia.backend.dto.AdminObservabilityResponse;
import com.storia.backend.lore.CharacterLoreService;
import com.storia.backend.rag.RagMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 AI Agent 실행 관측 조회 (읽기 전용). {@code /api/admin/**}이므로 관리자 인증이 적용된다. */
@RestController
@RequestMapping("/api/admin/ai/observability")
@RequiredArgsConstructor
public class AdminObservabilityController {

    private static final int RECENT_LIMIT = 50;

    private final AgentTelemetryStore telemetryStore;
    private final RagMetrics ragMetrics;
    private final CharacterLoreService characterLoreService;

    @GetMapping
    public AdminObservabilityResponse get() {
        return new AdminObservabilityResponse(telemetryStore.summary(), telemetryStore.recent(RECENT_LIMIT),
                ragMetrics.summary(), characterLoreService.summary());
    }
}
