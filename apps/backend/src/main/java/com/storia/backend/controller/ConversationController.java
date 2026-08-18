package com.storia.backend.controller;

import com.storia.backend.dto.MessageResponse;
import com.storia.backend.service.ConversationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping("/{characterId}/messages")
    public List<MessageResponse> getMessages(
            @PathVariable Long characterId,
            @RequestHeader("X-Device-Id") String deviceId) {
        return conversationService.getMessages(deviceId, characterId).stream()
                .map(MessageResponse::from)
                .toList();
    }
}
