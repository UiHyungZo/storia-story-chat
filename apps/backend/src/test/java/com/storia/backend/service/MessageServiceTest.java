package com.storia.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.storia.backend.entity.Character;
import com.storia.backend.entity.Conversation;
import com.storia.backend.entity.Message;
import com.storia.backend.entity.User;
import com.storia.backend.repository.MessageRepository;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

    private Character characterWithVoice(String voice) {
        Character character = new Character();
        character.setTtsVoiceId(voice);
        return character;
    }

    private void givenMessage(long id, Character character, String content) {
        Message message = new Message(new Conversation(new User("device"), character), Message.Role.ASSISTANT, content);
        when(messageRepository.findById(id)).thenReturn(Optional.of(message));
    }

    @Test
    void synthesizeAudio_reusesAudioForRepeatedRequests() {
        // 클라이언트 재생 1회 = HEAD + 플레이어 GET 2회 → 합성은 1회여야 한다.
        givenMessage(1L, characterWithVoice("v"), "안녕하세요");
        byte[] audio = new byte[] {1, 2, 3};
        when(ttsService.synthesize("안녕하세요", "v")).thenReturn(audio);

        assertThat(messageService.synthesizeAudio(1L)).isEqualTo(audio);
        assertThat(messageService.synthesizeAudio(1L)).isEqualTo(audio);
        assertThat(messageService.synthesizeAudio(1L)).isEqualTo(audio);

        verify(ttsService, times(1)).synthesize("안녕하세요", "v");
    }

    @Test
    void synthesizeAudio_doesNotCacheFailures() {
        givenMessage(1L, characterWithVoice("v"), "안녕하세요");
        byte[] audio = new byte[] {7};
        when(ttsService.synthesize("안녕하세요", "v")).thenReturn(null, audio);

        assertThat(messageService.synthesizeAudio(1L)).isNull();
        assertThat(messageService.synthesizeAudio(1L)).isEqualTo(audio);

        verify(ttsService, times(2)).synthesize("안녕하세요", "v");
    }

    @Test
    void synthesizeAudio_resynthesizesWhenCharacterVoiceChanges() {
        Character character = characterWithVoice("v1");
        givenMessage(1L, character, "안녕하세요");
        when(ttsService.synthesize("안녕하세요", "v1")).thenReturn(new byte[] {1});
        when(ttsService.synthesize("안녕하세요", "v2")).thenReturn(new byte[] {2});

        assertThat(messageService.synthesizeAudio(1L)).containsExactly(1);
        character.setTtsVoiceId("v2");
        assertThat(messageService.synthesizeAudio(1L)).containsExactly(2);
    }

    @Test
    void synthesizeAudio_concurrentRequestsShareOneSynthesis() throws Exception {
        givenMessage(1L, characterWithVoice("v"), "안녕하세요");
        byte[] audio = new byte[] {9};
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(ttsService.synthesize("안녕하세요", "v")).thenAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return audio;
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<byte[]> first = pool.submit(() -> messageService.synthesizeAudio(1L));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            Future<byte[]> second = pool.submit(() -> messageService.synthesizeAudio(1L));
            Thread.sleep(100); // 두 번째 요청이 진행 중인 합성을 기다리는 상태가 되도록
            release.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(audio);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(audio);
        } finally {
            pool.shutdownNow();
        }
        verify(ttsService, times(1)).synthesize("안녕하세요", "v");
    }

    @Test
    void synthesizeAudio_evictsLeastRecentlyUsedBeyondByteLimit() {
        int size = (int) (MessageService.MAX_CACHED_AUDIO_BYTES / 2) + 1; // 두 개를 함께 담을 수 없는 크기
        givenMessage(1L, characterWithVoice("v"), "첫 번째");
        givenMessage(2L, characterWithVoice("v"), "두 번째");
        when(ttsService.synthesize("첫 번째", "v")).thenReturn(new byte[size]);
        when(ttsService.synthesize("두 번째", "v")).thenReturn(new byte[size]);

        messageService.synthesizeAudio(1L);
        messageService.synthesizeAudio(2L); // 1번이 밀려남
        messageService.synthesizeAudio(2L); // 캐시 적중
        messageService.synthesizeAudio(1L); // 다시 합성

        verify(ttsService, times(2)).synthesize("첫 번째", "v");
        verify(ttsService, times(1)).synthesize("두 번째", "v");
    }
}
