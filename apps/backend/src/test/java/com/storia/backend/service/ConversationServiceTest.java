package com.storia.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.storia.backend.entity.Character;
import com.storia.backend.entity.Conversation;
import com.storia.backend.entity.Message;
import com.storia.backend.entity.User;
import com.storia.backend.repository.CharacterRepository;
import com.storia.backend.repository.ConversationRepository;
import com.storia.backend.repository.MessageRepository;
import com.storia.backend.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    private static final String DEVICE_ID = "device-1";
    private static final Long CHARACTER_ID = 10L;

    @Mock private UserRepository userRepository;
    @Mock private CharacterRepository characterRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private PushNotificationService pushNotificationService;

    @InjectMocks private ConversationService conversationService;

    private User user;
    private Character character;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        user = new User(DEVICE_ID);
        user.setId(1L);
        user.setFcmToken("fcm-token");

        character = new Character();
        character.setId(CHARACTER_ID);
        character.setName("루나");
        character.setSystemPrompt("system prompt");

        conversation = new Conversation(user, character);
        conversation.setId(100L);
    }

    @Test
    void postMessage_reusesExistingUserAndConversation() {
        when(userRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(user));
        when(characterRepository.findById(CHARACTER_ID)).thenReturn(Optional.of(character));
        when(conversationRepository.findByUserIdAndCharacterId(user.getId(), CHARACTER_ID))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message result = conversationService.postMessage(DEVICE_ID, CHARACTER_ID, "안녕");

        assertThat(result.getRole()).isEqualTo(Message.Role.USER);
        assertThat(result.getContent()).isEqualTo("안녕");
        assertThat(result.getConversation()).isEqualTo(conversation);
        verify(userRepository, never()).save(any());
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void postMessage_createsUserAndConversationOnFirstContact() {
        when(userRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(characterRepository.findById(CHARACTER_ID)).thenReturn(Optional.of(character));
        when(conversationRepository.findByUserIdAndCharacterId(user.getId(), CHARACTER_ID))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenReturn(conversation);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        conversationService.postMessage(DEVICE_ID, CHARACTER_ID, "첫 메시지");

        verify(userRepository).save(any(User.class));
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    void postMessage_throwsWhenCharacterDoesNotExist() {
        when(userRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(user));
        when(characterRepository.findById(CHARACTER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.postMessage(DEVICE_ID, CHARACTER_ID, "안녕"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(CHARACTER_ID));
    }

    @Test
    void postAssistantMessage_savesMessageAndSendsPush() {
        when(userRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(user));
        when(characterRepository.findById(CHARACTER_ID)).thenReturn(Optional.of(character));
        when(conversationRepository.findByUserIdAndCharacterId(user.getId(), CHARACTER_ID))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message result = conversationService.postAssistantMessage(DEVICE_ID, CHARACTER_ID, "응답");

        assertThat(result.getRole()).isEqualTo(Message.Role.ASSISTANT);
        verify(pushNotificationService).sendNewMessage(eq("fcm-token"), eq("루나"), eq("응답"));
    }

    @Test
    void getMessages_returnsRepositoryResultForResolvedConversation() {
        when(userRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(user));
        when(characterRepository.findById(CHARACTER_ID)).thenReturn(Optional.of(character));
        when(conversationRepository.findByUserIdAndCharacterId(user.getId(), CHARACTER_ID))
                .thenReturn(Optional.of(conversation));
        Message existing = new Message(conversation, Message.Role.USER, "old");
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId()))
                .thenReturn(java.util.List.of(existing));

        var result = conversationService.getMessages(DEVICE_ID, CHARACTER_ID);

        assertThat(result).containsExactly(existing);
    }
}
