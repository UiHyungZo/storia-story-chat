package com.storia.backend.controller;

import com.storia.backend.ai.AdminAgentService;
import com.storia.backend.dto.AdminAgentRequest;
import com.storia.backend.dto.AdminAgentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 AI Agent. {@code /api/admin/**}이므로 AdminAuthInterceptor 인증이 자동 적용된다. */
@RestController
@RequestMapping("/api/admin/ai/agent")
@RequiredArgsConstructor
public class AdminAgentController {

    private final AdminAgentService adminAgentService;

    @PostMapping("/query")
    public AdminAgentResponse query(@Valid @RequestBody AdminAgentRequest request) {
        return adminAgentService.ask(request.question());
    }
}
