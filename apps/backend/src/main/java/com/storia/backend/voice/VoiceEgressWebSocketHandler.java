package com.storia.backend.voice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * LiveKit Track Egress connects here (see VoiceCallService#startTurn) and streams
 * raw pcm_s16le audio for one voice turn as binary WS frames, plus occasional text
 * frames for track state events (e.g. `{"muted": true}`) that we don't need. The
 * connection closes when the client unpublishes the track — that's our signal the
 * turn's audio is complete.
 *
 * turnId identifies which VoiceTurnSession the audio belongs to, passed as a query
 * param on the egress websocket URL — LiveKit's egress connection has no built-in
 * auth, so the turnId (a UUID) doubles as an unguessable bearer for this stream.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VoiceEgressWebSocketHandler extends BinaryWebSocketHandler {

    private static final String TURN_ID_ATTR = "turnId";

    private final VoiceCallService voiceCallService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String turnId = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("turnId");
        session.getAttributes().put(TURN_ID_ATTR, turnId);
        log.info("Egress WS connected (turnId={})", turnId);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        String turnId = (String) session.getAttributes().get(TURN_ID_ATTR);
        if (turnId == null) {
            return;
        }
        byte[] chunk = new byte[message.getPayloadLength()];
        message.getPayload().get(chunk);
        voiceCallService.appendAudio(turnId, chunk);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.debug("Egress WS event: {}", message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String turnId = (String) session.getAttributes().get(TURN_ID_ATTR);
        if (turnId != null) {
            voiceCallService.completeTurn(turnId);
        }
    }
}
