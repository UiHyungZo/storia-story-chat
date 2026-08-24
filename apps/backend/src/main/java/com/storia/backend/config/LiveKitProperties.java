package com.storia.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * `host` is the bare LiveKit project host (e.g. `myproject.livekit.cloud`, no
 * scheme) — the client connects to it as `wss://`, the server admin API as
 * `https://`. `egressAudioWsUrl` is a DIFFERENT thing: OUR backend's own
 * publicly-reachable WebSocket URL (e.g. an ngrok tunnel in local dev) that
 * LiveKit's Track Egress connects back to with raw audio — see
 * VoiceEgressWebSocketConfig/VoiceEgressWebSocketHandler.
 */
@ConfigurationProperties(prefix = "livekit")
public record LiveKitProperties(String host, String apiKey, String apiSecret, String egressAudioWsUrl) {

    public boolean isConfigured() {
        return notBlank(host) && notBlank(apiKey) && notBlank(apiSecret) && notBlank(egressAudioWsUrl);
    }

    public String wsUrl() {
        return "wss://" + host;
    }

    public String apiUrl() {
        return "https://" + host;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
