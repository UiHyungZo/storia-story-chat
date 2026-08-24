package com.storia.backend.controller;

import com.storia.backend.dto.CallTokenResponse;
import com.storia.backend.dto.StartTurnRequest;
import com.storia.backend.dto.StartTurnResponse;
import com.storia.backend.dto.TurnStatusResponse;
import com.storia.backend.voice.VoiceCallService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 축소판 A안(PRD 3.9) — 클라이언트가 LiveKit으로 실제 WebRTC 오디오를 서버까지 보내고
 * 서버는 그 오디오를 기존 배치 STT/Gemini/TTS 파이프라인(B안)에 흘려보낸다.
 */
@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
public class VoiceCallController {

    private final VoiceCallService voiceCallService;

    @PostMapping("/{characterId}/token")
    public ResponseEntity<CallTokenResponse> issueToken(
            @PathVariable Long characterId,
            @RequestHeader("X-Device-Id") String deviceId) {
        if (!voiceCallService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok(voiceCallService.createToken(deviceId, characterId));
    }

    @PostMapping("/{characterId}/turns")
    public ResponseEntity<StartTurnResponse> startTurn(
            @PathVariable Long characterId,
            @RequestHeader("X-Device-Id") String deviceId,
            @Valid @RequestBody StartTurnRequest request) {
        if (!voiceCallService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        String turnId = voiceCallService.startTurn(deviceId, characterId, request.roomName(), request.trackSid());
        return ResponseEntity.ok(new StartTurnResponse(turnId));
    }

    @GetMapping("/turns/{turnId}")
    public TurnStatusResponse getTurnStatus(@PathVariable String turnId) {
        return voiceCallService.getStatus(turnId);
    }
}
