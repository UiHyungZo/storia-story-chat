package com.storia.backend.dto;

import com.storia.backend.entity.Message;
import java.time.Instant;

public record MessageResponse(
        Long id,
        String role,
        String content,
        Instant createdAt) {

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getCreatedAt());
    }
}
