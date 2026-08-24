package com.storia.backend.voice;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory only — turns are ephemeral; the final exchange lands in Message rows via ConversationService. */
@Component
public class VoiceTurnRegistry {

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
}
