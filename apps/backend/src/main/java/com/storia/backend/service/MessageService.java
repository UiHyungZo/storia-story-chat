package com.storia.backend.service;

import com.storia.backend.entity.Message;
import com.storia.backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final TtsService ttsService;

    /** Null if the message doesn't exist or TTS isn't available for it. */
    public byte[] synthesizeAudio(Long messageId) {
        return messageRepository.findById(messageId)
                .map(Message::getContent)
                .map(ttsService::synthesize)
                .orElse(null);
    }
}
