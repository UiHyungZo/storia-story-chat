package com.storia.backend.voice;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** In-memory only — turns are ephemeral; the final exchange lands in Message rows via ConversationService. */
@Component
@Slf4j
public class VoiceTurnRegistry {

    // startTrackEgress()는 목적지(egress WS URL) 연결 가능 여부와 무관하게 성공 응답을 줘서,
    // 실제로 egress가 붙지 못하면 세션이 RECORDING에서 영원히 안 끝나고 메모리에 남는다
    // (2026-09-13 재현 확인). 클라이언트 폴링 타임아웃(75초)보다 넉넉하게 잡아 정상 처리
    // 중인 턴을 오탐하지 않게 함.
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(5);

    private final Map<String, VoiceTurnSession> sessions = new ConcurrentHashMap<>();

    public VoiceTurnSession create(String deviceId, Long characterId) {
        VoiceTurnSession session = new VoiceTurnSession(UUID.randomUUID().toString(), deviceId, characterId);
        sessions.put(session.getTurnId(), session);
        return session;
    }

    public VoiceTurnSession get(String turnId) {
        return sessions.get(turnId);
    }

    public void remove(String turnId) {
        sessions.remove(turnId);
    }

    /** 운영 콘솔 세션 상태 조회용 스냅샷. ConcurrentHashMap을 직접 노출하지 않기 위해 방어적으로 복사한다. */
    public Collection<VoiceTurnSession> all() {
        return List.copyOf(sessions.values());
    }

    @Scheduled(fixedRate = 60_000)
    public void sweepStaleSessions() {
        Instant threshold = Instant.now().minus(STALE_THRESHOLD);
        sessions.values().stream()
                .filter(session -> !session.isTerminal() && session.getCreatedAt().isBefore(threshold))
                .forEach(session -> {
                    log.warn(
                            "음성 턴 {}이(가) {}분 넘게 끝나지 않아 정리함 (egress 미도달 추정)",
                            session.getTurnId(),
                            STALE_THRESHOLD.toMinutes());
                    session.fail("응답 시간이 초과되었습니다.");
                    sessions.remove(session.getTurnId());
                });
    }
}
