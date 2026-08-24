package com.storia.backend.controller;

import com.storia.backend.dto.MessageRequest;
import com.storia.backend.dto.MessageResponse;
import com.storia.backend.service.ConversationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @PostMapping("/{characterId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse postMessage(
            @PathVariable Long characterId,
            @RequestHeader("X-Device-Id") String deviceId,
            @Valid @RequestBody MessageRequest request) {
        return MessageResponse.from(
                conversationService.postMessage(deviceId, characterId, request.content()));
    }
}
