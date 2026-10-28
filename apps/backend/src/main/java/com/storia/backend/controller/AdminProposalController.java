package com.storia.backend.controller;

import com.storia.backend.ai.ProposalApprovalService;
import com.storia.backend.dto.AdminProposalDecisionRequest;
import com.storia.backend.dto.AdminProposalResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI가 만든 캐릭터 설정 변경 제안의 검토·승인·거절. {@code /api/admin/**}이므로 관리자 인증이 적용된다. */
@RestController
@RequestMapping("/api/admin/ai/proposals")
@RequiredArgsConstructor
public class AdminProposalController {

    private final ProposalApprovalService proposalApprovalService;

    @GetMapping
    public List<AdminProposalResponse> list() {
        return proposalApprovalService.list();
    }

    @GetMapping("/{id}")
    public AdminProposalResponse get(@PathVariable String id) {
        return proposalApprovalService.get(id);
    }

    @PostMapping("/{id}/approve")
    public AdminProposalResponse approve(
            @PathVariable String id, @RequestBody(required = false) AdminProposalDecisionRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.confirm())) {
            throw new IllegalArgumentException("승인하려면 confirm=true가 필요합니다.");
        }
        return proposalApprovalService.approve(id);
    }

    @PostMapping("/{id}/reject")
    public AdminProposalResponse reject(@PathVariable String id) {
        return proposalApprovalService.reject(id);
    }
}
