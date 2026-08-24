package com.storia.backend.voice;

import com.storia.backend.config.LiveKitProperties;
import com.storia.backend.dto.CallTokenResponse;
import com.storia.backend.dto.TurnStatusResponse;
import com.storia.backend.entity.Message;
import com.storia.backend.service.ConversationService;
import com.storia.backend.service.GeminiService;
import com.storia.backend.service.SttService;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanSubscribe;
import io.livekit.server.LiveKitAPI;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import java.io.IOException;
import java.util.stream.Collectors;
import livekit.LivekitEgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import retrofit2.Response;

/**
 * 축소판 A안(PRD 3.9, docs/decisions.md ADR-004 갱신 항목): 클라이언트가 LiveKit으로
 * 실제 WebRTC 오디오를 서버까지 보내고(Track Egress → VoiceEgressWebSocketHandler),
 * 서버는 그 오디오를 기존 배치 STT/Gemini/TTS 파이프라인(B안, ConversationService/
 * TtsService)에 그대로 흘려보낸다. 서버가 합성 음성을 다시 WebRTC로 되쏘는 완전한
 * 양방향 실시간(A안)은 이번엔 범위 밖 — 응답은 기존 B안처럼 오디오 URL로 반환됨.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceCallService {

    private final LiveKitProperties properties;
    private final VoiceTurnRegistry turnRegistry;
    private final SttService sttService;
    private final GeminiService geminiService;
    private final ConversationService conversationService;

    private volatile LiveKitAPI cachedClient;

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public CallTokenResponse createToken(String deviceId, Long characterId) {
        String roomName = roomNameFor(deviceId, characterId);
        AccessToken token = new AccessToken(properties.apiKey(), properties.apiSecret());
        token.setIdentity(deviceId);
        // CanSubscribe(true): 축소판 A안 자체는 클라이언트가 room 안의 다른 트랙을 구독할
        // 필요가 없었지만, apps/python-sidecar(완전한 A안 확장)가 이 room에 봇으로 들어와
        // TTS 오디오 트랙을 publish하면 클라이언트가 그걸 들으려면 구독 권한이 있어야 함.
        token.addGrants(new RoomJoin(true), new RoomName(roomName), new CanPublish(true), new CanSubscribe(true));
        return new CallTokenResponse(token.toJwt(), properties.wsUrl(), roomName);
    }

    public String startTurn(String deviceId, Long characterId, String roomName, String trackSid) {
        VoiceTurnSession session = turnRegistry.create(deviceId, characterId);
        String egressWsUrl = properties.egressAudioWsUrl() + "?turnId=" + session.getTurnId();
        try {
            Response<LivekitEgress.EgressInfo> response =
                    client().getEgress().startTrackEgress(roomName, egressWsUrl, trackSid).execute();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Egress 요청 실패: HTTP " + response.code());
            }
        } catch (IOException | IllegalStateException e) {
            turnRegistry.remove(session.getTurnId());
            throw new IllegalStateException("Egress 시작 실패", e);
        }
        return session.getTurnId();
    }

    public void appendAudio(String turnId, byte[] chunk) {
        VoiceTurnSession session = turnRegistry.get(turnId);
        if (session != null) {
            session.appendAudio(chunk);
        }
    }

    @Async
    public void completeTurn(String turnId) {
        VoiceTurnSession session = turnRegistry.get(turnId);
        if (session == null) {
            return;
        }
        session.markProcessing();
        try {
            String transcript = sttService.transcribe(session.audioBytes());
            if (transcript == null || transcript.isBlank()) {
                session.fail("음성을 인식하지 못했어요.");
                return;
            }

            conversationService.postMessage(session.getDeviceId(), session.getCharacterId(), transcript);
            String reply = geminiService
                    .streamReply(
                            conversationService.getSystemPrompt(session.getCharacterId()),
                            conversationService.getMessages(session.getDeviceId(), session.getCharacterId()))
                    .collect(Collectors.joining())
                    .defaultIfEmpty("죄송해요, 지금은 답변을 생성할 수 없어요.")
                    .onErrorReturn("죄송해요, 지금은 답변을 생성할 수 없어요.")
                    .block();

            Message assistantMessage =
                    conversationService.postAssistantMessage(session.getDeviceId(), session.getCharacterId(), reply);
            session.complete(assistantMessage.getId());
        } catch (Exception e) {
            log.warn("음성 턴 처리 실패 (turnId={})", turnId, e);
            session.fail("응답 생성에 실패했습니다.");
        }
    }

    public TurnStatusResponse getStatus(String turnId) {
        VoiceTurnSession session = turnRegistry.get(turnId);
        if (session == null) {
            return new TurnStatusResponse("error", null, "알 수 없는 통화 턴입니다.");
        }
        return new TurnStatusResponse(
                session.getStatus().name().toLowerCase(), session.getAssistantMessageId(), session.getErrorMessage());
    }

    private LiveKitAPI client() {
        if (cachedClient == null) {
            cachedClient = LiveKitAPI.createClient(properties.apiUrl(), properties.apiKey(), properties.apiSecret());
        }
        return cachedClient;
    }

    private static String roomNameFor(String deviceId, Long characterId) {
        return "call-" + deviceId + "-" + characterId;
    }
}
