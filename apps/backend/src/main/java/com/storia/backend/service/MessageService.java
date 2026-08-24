package com.storia.backend.service;

import com.storia.backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final TtsService ttsService;

    /** Null if the message doesn't exist or TTS isn't available for it. */
    @Transactional(readOnly = true)
    public byte[] synthesizeAudio(Long messageId) {
        return messageRepository.findById(messageId)
                .map(message -> ttsService.synthesize(
                        message.getContent(),
                        message.getConversation().getCharacter().getTtsVoiceId()))
                .orElse(null);
    }
}
