package com.storia.backend.service;

import com.storia.backend.entity.Message;
import com.storia.backend.repository.MessageRepository;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageService {

    /**
     * 합성한 오디오의 메모리 캐시 상한(바이트 합계). 클라이언트는 재생 한 번에 HEAD(가용성 확인) + 오디오 플레이어
     * GET(iOS는 2회까지 관측)을 보내고, 캐시가 없으면 요청마다 Google TTS를 다시 호출했다(2026-11-01 실기기: 3회).
     * 응답 하나는 수백 KB라 약 20~30개를 담는다. 재시작하면 비워진다.
     */
    static final long MAX_CACHED_AUDIO_BYTES = 16L * 1024 * 1024;

    private final MessageRepository messageRepository;
    private final TtsService ttsService;

    /** 접근 순서 LRU. 성공한 합성 결과만 담는다. {@code this}로 동기화한다. */
    private final LinkedHashMap<String, byte[]> audioCache = new LinkedHashMap<>(16, 0.75f, true);
    /** 같은 메시지에 대한 동시 요청(예: HEAD와 GET)이 합성 한 번을 공유하게 한다. */
    private final Map<String, CompletableFuture<byte[]>> inFlight = new ConcurrentHashMap<>();
    private long cachedAudioBytes;

    /** Null if the message doesn't exist or TTS isn't available for it. */
    @Transactional(readOnly = true)
    public byte[] synthesizeAudio(Long messageId) {
        return messageRepository.findById(messageId)
                .map(message -> synthesizeOnce(messageId, message))
                .orElse(null);
    }

    private byte[] synthesizeOnce(Long messageId, Message message) {
        String voiceId = message.getConversation().getCharacter().getTtsVoiceId();
        // 메시지 본문은 바뀌지 않지만 관리자가 캐릭터 보이스를 바꿀 수 있으므로 보이스를 키에 포함한다.
        String key = messageId + "|" + voiceId;
        byte[] cached = cachedAudio(key);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<byte[]> mine = new CompletableFuture<>();
        CompletableFuture<byte[]> running = inFlight.putIfAbsent(key, mine);
        if (running != null) {
            return running.join();
        }
        byte[] audio = null;
        try {
            // 앞선 합성이 캐시에 넣고 in-flight에서 빠진 직후에 들어온 요청이면 다시 합성하지 않는다.
            audio = cachedAudio(key);
            if (audio == null) {
                audio = ttsService.synthesize(message.getContent(), voiceId);
                if (audio != null) {
                    cacheAudio(key, audio);
                }
            }
            return audio;
        } finally {
            inFlight.remove(key, mine);
            mine.complete(audio);
        }
    }

    private synchronized byte[] cachedAudio(String key) {
        return audioCache.get(key);
    }

    private synchronized void cacheAudio(String key, byte[] audio) {
        if (audio.length > MAX_CACHED_AUDIO_BYTES) {
            return;
        }
        byte[] previous = audioCache.put(key, audio);
        if (previous != null) {
            cachedAudioBytes -= previous.length;
        }
        cachedAudioBytes += audio.length;
        Iterator<Map.Entry<String, byte[]>> eldest = audioCache.entrySet().iterator();
        while (cachedAudioBytes > MAX_CACHED_AUDIO_BYTES && eldest.hasNext()) {
            cachedAudioBytes -= eldest.next().getValue().length;
            eldest.remove();
        }
    }
}
