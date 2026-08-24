package com.storia.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.storia.backend.entity.Character;
import com.storia.backend.entity.Conversation;
import com.storia.backend.entity.Message;
import com.storia.backend.entity.User;
import com.storia.backend.repository.MessageRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock private MessageRepository messageRepository;
    @Mock private TtsService ttsService;

    @InjectMocks private MessageService messageService;

    @Test
    void synthesizeAudio_returnsNull_whenMessageDoesNotExist() {
        when(messageRepository.findById(999L)).thenReturn(Optional.empty());

        byte[] result = messageService.synthesizeAudio(999L);

        assertThat(result).isNull();
        verifyNoInteractions(ttsService);
    }

    @Test
    void synthesizeAudio_delegatesMessageContentAndCharacterVoiceToTtsService() {
        Character character = new Character();
        character.setTtsVoiceId("ko-KR-Standard-A");
        Conversation conversation = new Conversation(new User("device"), character);
        Message message = new Message(conversation, Message.Role.ASSISTANT, "안녕하세요");
        byte[] audio = new byte[] {1, 2, 3};
        when(messageRepository.findById(1L)).thenReturn(Optional.of(message));
        when(ttsService.synthesize("안녕하세요", "ko-KR-Standard-A")).thenReturn(audio);

        byte[] result = messageService.synthesizeAudio(1L);

        assertThat(result).isEqualTo(audio);
    }

    @Test
    void synthesizeAudio_returnsNull_whenTtsServiceReturnsNull() {
        Conversation conversation = new Conversation(new User("device"), new Character());
        Message message = new Message(conversation, Message.Role.ASSISTANT, "안녕하세요");
        when(messageRepository.findById(1L)).thenReturn(Optional.of(message));
        when(ttsService.synthesize(any(), any())).thenReturn(null);

        assertThat(messageService.synthesizeAudio(1L)).isNull();
    }
}
