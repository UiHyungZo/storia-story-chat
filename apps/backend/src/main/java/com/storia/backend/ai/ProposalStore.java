package com.storia.backend.ai;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 변경 제안을 메모리에만 보관한다 ({@link com.storia.backend.admin.AdminSessionStore}와 같은 방식).
 * 서버 재기동 시 제안이 사라지는 것은 허용한다 — 승인되지 않은 제안은 어떤 데이터도 바꾸지 않았기 때문.
 * 만료·개수 제한은 별도 스케줄러 없이 접근 시점에 정리한다.
 */
@Component
public class ProposalStore {

    static final int MAX_PENDING = 50;
    static final Duration TTL = Duration.ofHours(24);

    private final Map<String, CharacterChangeProposal> proposals = new ConcurrentHashMap<>();

    /** 대기 중 제안이 한도에 도달하면 empty. */
    public synchronized Optional<CharacterChangeProposal> add(
            Long characterId, Map<String, String> proposedFields, String reason, String baseFingerprint) {
        purgeExpired();
        long pending = proposals.values().stream().filter(p -> p.getStatus() == ProposalStatus.PENDING).count();
        if (pending >= MAX_PENDING) {
            return Optional.empty();
        }
        CharacterChangeProposal proposal = new CharacterChangeProposal(
                UUID.randomUUID().toString(), characterId, proposedFields, reason, baseFingerprint, Instant.now());
        proposals.put(proposal.getId(), proposal);
        return Optional.of(proposal);
    }

    public Optional<CharacterChangeProposal> find(String id) {
        purgeExpired();
        return id == null ? Optional.empty() : Optional.ofNullable(proposals.get(id));
    }

    public List<CharacterChangeProposal> list() {
        purgeExpired();
        return proposals.values().stream()
                .sorted(Comparator.comparing(CharacterChangeProposal::getCreatedAt).reversed())
                .toList();
    }

    private void purgeExpired() {
        Instant cutoff = Instant.now().minus(TTL);
        proposals.values().removeIf(p -> p.getCreatedAt().isBefore(cutoff));
    }
}
