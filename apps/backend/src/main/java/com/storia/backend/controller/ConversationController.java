package com.storia.backend.controller;

import com.storia.backend.dto.MessageExchangeResponse;
import com.storia.backend.dto.MessageRequest;
import com.storia.backend.dto.MessageResponse;
import com.storia.backend.entity.Message;
import com.storia.backend.service.ConversationService;
import com.storia.backend.service.GeminiService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
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
    private final GeminiService geminiService;

    @GetMapping("/{characterId}/messages")
    public List<MessageResponse> getMessages(
            @PathVariable Long characterId,
            @RequestHeader("X-Device-Id") String deviceId) {
        return conversationService.getMessages(deviceId, characterId).stream()
                .map(MessageResponse::from)
                .toList();
    }

    /**
     * WebSocket(STOMP) 스트리밍 실패 시 클라이언트가 쓰는 폴백 경로. 스트리밍 대신 Gemini 응답
     * 전체를 기다렸다가 한 번에 반환한다 (REST 왕복이므로 타이핑 효과는 없음).
     */
    @PostMapping("/{characterId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageExchangeResponse postMessage(
            @PathVariable Long characterId,
            @RequestHeader("X-Device-Id") String deviceId,
            @Valid @RequestBody MessageRequest request) {
        Message userMessage = conversationService.postMessage(deviceId, characterId, request.content());

        String reply = geminiService
                .streamReply(
                        conversationService.getSystemPrompt(characterId),
                        conversationService.getMessages(deviceId, characterId))
                .collect(Collectors.joining())
                .defaultIfEmpty("죄송해요, 지금은 답변을 생성할 수 없어요.")
                .onErrorReturn("죄송해요, 지금은 답변을 생성할 수 없어요.")
                .block();

        Message assistantMessage = conversationService.postAssistantMessage(deviceId, characterId, reply);

        return new MessageExchangeResponse(
                MessageResponse.from(userMessage), MessageResponse.from(assistantMessage));
    }
}
