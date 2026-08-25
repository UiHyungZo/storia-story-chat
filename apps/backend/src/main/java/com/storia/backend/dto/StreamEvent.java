package com.storia.backend.dto;

import java.time.Instant;

/**
 * Published to /topic/conversation/{deviceId}/{characterId} while a Gemini reply streams in.
 */
public record StreamEvent(String type, Long messageId, String content, Instant createdAt) {

    public static StreamEvent chunk(String content) {
        return new StreamEvent("CHUNK", null, content, null);
    }

    public static StreamEvent done(MessageResponse message) {
        return new StreamEvent("DONE", message.id(), message.content(), message.createdAt());
    }

    public static StreamEvent error(String message) {
        return new StreamEvent("ERROR", null, message, null);
    }
}
