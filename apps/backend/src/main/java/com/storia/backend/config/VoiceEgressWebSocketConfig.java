package com.storia.backend.config;

import com.storia.backend.voice.VoiceEgressWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Separate raw WebSocket endpoint from the STOMP one in WebSocketConfig — LiveKit's
 * Track Egress speaks plain WebSocket binary frames, not STOMP, so it needs its own
 * handler at its own path rather than going through the STOMP message broker.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class VoiceEgressWebSocketConfig implements WebSocketConfigurer {

    private final VoiceEgressWebSocketHandler handler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/egress/audio").setAllowedOrigins("*");
    }
}
