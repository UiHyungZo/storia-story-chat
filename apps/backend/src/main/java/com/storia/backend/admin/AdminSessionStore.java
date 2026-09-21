package com.storia.backend.admin;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 관리자 세션 토큰을 메모리에만 보관한다. 계정이 하나뿐인 운영 콘솔이라 DB 테이블 없이도 충분하며,
 * {@link com.storia.backend.voice.VoiceTurnRegistry}와 같은 in-memory + 주기적 sweep 패턴을 따른다.
 * 서버 재기동 시 모든 세션이 사라지므로 관리자는 다시 로그인해야 한다 — 허용 가능한 트레이드오프.
 */
@Component
@Slf4j
public class AdminSessionStore {

    private static final Duration SESSION_TTL = Duration.ofDays(7);
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Instant> tokens = new ConcurrentHashMap<>();

    public String issue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.put(token, Instant.now().plus(SESSION_TTL));
        return token;
    }

    public boolean isValid(String token) {
        if (token == null) {
            return false;
        }
        Instant expiry = tokens.get(token);
        return expiry != null && expiry.isAfter(Instant.now());
    }

    public void invalidate(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    @Scheduled(fixedRate = 3_600_000)
    public void sweepExpired() {
        Instant now = Instant.now();
        tokens.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
