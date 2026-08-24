package com.storia.backend.controller;

import com.storia.backend.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * On-demand TTS for a saved message (PRD 3.9 음성 통화 B안 — "오디오 파일 URL 응답").
 * Not tied to X-Device-Id/conversation ownership since a message id is an opaque
 * sequential id with no sensitive content beyond the chat text itself, same trust
 * level as the rest of this device-id-only-auth app (PRD 9절 — 정식 인증 범위 밖).
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/{messageId}/audio")
    public ResponseEntity<byte[]> getAudio(@PathVariable Long messageId) {
        byte[] audio = messageService.synthesizeAudio(messageId);
        if (audio == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("audio/mpeg"))
                .body(audio);
    }
}
