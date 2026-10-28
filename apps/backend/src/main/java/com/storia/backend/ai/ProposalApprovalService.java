package com.storia.backend.ai;

import com.storia.backend.dto.AdminProposalResponse;
import com.storia.backend.dto.AdminProposalResponse.Fields;
import com.storia.backend.entity.Character;
import com.storia.backend.exception.ConflictException;
import com.storia.backend.exception.ResourceNotFoundException;
import com.storia.backend.service.CharacterService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 제안의 조회·승인·거절. 캐릭터 설정을 실제로 바꾸는 유일한 경로이며, 인증된 관리자의 별도 HTTP 요청
 * ({@link com.storia.backend.controller.AdminProposalController})으로만 호출된다. 에이전트 루프
 * ({@link AdminAgentService}, {@link AdminToolRegistry})는 이 서비스를 참조하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ProposalApprovalService {

    private final ProposalStore proposalStore;
    private final CharacterService characterService;

    public List<AdminProposalResponse> list() {
        return proposalStore.list().stream().map(this::toResponse).toList();
    }

    public AdminProposalResponse get(String id) {
        return toResponse(require(id));
    }

    /** 거절은 상태만 바꾸며 캐릭터를 조회·수정하지 않는다. */
    public AdminProposalResponse reject(String id) {
        CharacterChangeProposal proposal = require(id);
        synchronized (proposal) {
            requirePending(proposal);
            proposal.setStatus(ProposalStatus.REJECTED);
        }
        return toResponse(proposal);
    }

    /**
     * 현재 설정이 제안 시점과 같을 때만, 제안된 필드를 현재 값에 병합해 기존 {@link CharacterService#update}
     * 경로로 저장한다. 그 사이 설정이 바뀌었으면 적용하지 않고 STALE로 표시한다.
     */
    public AdminProposalResponse approve(String id) {
        CharacterChangeProposal proposal = require(id);
        synchronized (proposal) {
            requirePending(proposal);
            Character current = characterService.findById(proposal.getCharacterId());
            if (!CharacterChangeProposal.fingerprint(current).equals(proposal.getBaseFingerprint())) {
                proposal.setStatus(ProposalStatus.STALE);
                throw new ConflictException("제안 이후 캐릭터 설정이 변경되어 적용할 수 없습니다. 새 제안을 만드세요.");
            }

            var fields = proposal.getProposedFields();
            String concept = fields.containsKey("concept") ? fields.get("concept") : current.getConcept();
            String systemPrompt = fields.containsKey("systemPrompt") ? fields.get("systemPrompt") : current.getSystemPrompt();
            String ttsVoiceId = fields.containsKey("ttsVoiceId") ? fields.get("ttsVoiceId") : current.getTtsVoiceId();
            if (concept == null || concept.isBlank() || systemPrompt == null || systemPrompt.isBlank()) {
                throw new IllegalArgumentException("concept와 systemPrompt는 비어 있을 수 없습니다.");
            }

            characterService.update(proposal.getCharacterId(), concept, systemPrompt, ttsVoiceId);
            proposal.setStatus(ProposalStatus.APPROVED);
        }
        return toResponse(proposal);
    }

    private CharacterChangeProposal require(String id) {
        return proposalStore.find(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proposal not found: " + id));
    }

    private void requirePending(CharacterChangeProposal proposal) {
        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new ConflictException("이미 처리된 제안입니다: " + proposal.getStatus());
        }
    }

    private AdminProposalResponse toResponse(CharacterChangeProposal proposal) {
        Character current = characterService.findById(proposal.getCharacterId());
        var fields = proposal.getProposedFields();
        boolean stale = proposal.getStatus() == ProposalStatus.STALE
                || (proposal.getStatus() == ProposalStatus.PENDING
                        && !CharacterChangeProposal.fingerprint(current).equals(proposal.getBaseFingerprint()));
        return new AdminProposalResponse(
                proposal.getId(),
                proposal.getCharacterId(),
                current.getName(),
                proposal.getStatus().name(),
                proposal.getReason(),
                proposal.getCreatedAt(),
                proposal.getDecidedAt(),
                stale,
                List.copyOf(fields.keySet()),
                new Fields(current.getConcept(), current.getSystemPrompt(), current.getTtsVoiceId()),
                new Fields(fields.get("concept"), fields.get("systemPrompt"), fields.get("ttsVoiceId")));
    }
}
