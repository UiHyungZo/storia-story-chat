package com.storia.backend.controller;

import com.storia.backend.dto.MessageRequest;
import com.storia.backend.dto.MessageResponse;
import com.storia.backend.dto.StreamEvent;
import com.storia.backend.entity.Message;
import com.storia.backend.service.ConversationService;
import com.storia.backend.service.GeminiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import reactor.core.scheduler.Schedulers;

/**
 * Client --publish--> /app/conversation/{characterId}/send
 * Server --broadcast--> /topic/conversation/{deviceId}/{characterId} (StreamEvent: CHUNK*, then DONE or ERROR)
 *
 * <p>The broadcast destination is scoped by deviceId (not just characterId) so that two
 * users chatting with the same character don't receive each other's stream events.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ConversationStompController {

    private final ConversationService conversationService;
    private final GeminiService geminiService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/conversation/{characterId}/send")
    public void handleMessage(
            @DestinationVariable Long characterId,
            @Header("X-Device-Id") String deviceId,
            @Valid @Payload MessageRequest request) {
        String content = request.content();
        conversationService.postMessage(deviceId, characterId, content);

        String destination = destinationFor(deviceId, characterId);
        StringBuilder full = new StringBuilder();

        geminiService.streamReply(
                        conversationService.getSystemPrompt(characterId),
                        conversationService.getMessages(deviceId, characterId))
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> {
                    full.append(chunk);
                    messagingTemplate.convertAndSend(destination, StreamEvent.chunk(chunk));
                })
                .doOnComplete(() -> {
                    if (full.isEmpty()) {
                        messagingTemplate.convertAndSend(destination,
                                StreamEvent.error("응답을 생성하지 못했습니다."));
                        return;
                    }
                    Message assistantMessage =
                            conversationService.postAssistantMessage(deviceId, characterId, full.toString());
                    messagingTemplate.convertAndSend(destination,
                            StreamEvent.done(MessageResponse.from(assistantMessage)));
                })
                .doOnError(error -> {
                    log.warn("Gemini streaming failed for character {}", characterId, error);
                    messagingTemplate.convertAndSend(destination,
                            StreamEvent.error("죄송해요, 지금은 답변을 생성할 수 없어요."));
                })
                .subscribe();
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    public void handleValidationError(
            @DestinationVariable Long characterId,
            @Header("X-Device-Id") String deviceId) {
        log.warn("Invalid message payload for character {}", characterId);
        messagingTemplate.convertAndSend(
                destinationFor(deviceId, characterId),
                StreamEvent.error("메시지를 보낼 수 없습니다."));
    }

    private String destinationFor(String deviceId, Long characterId) {
        return "/topic/conversation/" + deviceId + "/" + characterId;
    }
}
