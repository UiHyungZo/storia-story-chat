package com.storia.backend.service;

import com.storia.backend.entity.Character;
import com.storia.backend.entity.Conversation;
import com.storia.backend.entity.Message;
import com.storia.backend.entity.User;
import com.storia.backend.repository.CharacterRepository;
import com.storia.backend.repository.ConversationRepository;
import com.storia.backend.repository.MessageRepository;
import com.storia.backend.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final UserRepository userRepository;
    private final CharacterRepository characterRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final PushNotificationService pushNotificationService;

    @Transactional
    public List<Message> getMessages(String deviceId, Long characterId) {
        Conversation conversation = getOrCreateConversation(deviceId, characterId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
    }

    @Transactional
    public Message postMessage(String deviceId, Long characterId, String content) {
        Conversation conversation = getOrCreateConversation(deviceId, characterId);
        Message message = new Message(conversation, Message.Role.USER, content);
        return messageRepository.save(message);
    }

    @Transactional
    public Message postAssistantMessage(String deviceId, Long characterId, String content) {
        Conversation conversation = getOrCreateConversation(deviceId, characterId);
        Message message = new Message(conversation, Message.Role.ASSISTANT, content);
        Message saved = messageRepository.save(message);
        pushNotificationService.sendNewMessage(
                conversation.getUser().getFcmToken(), conversation.getCharacter().getName(), content);
        return saved;
    }

    public String getSystemPrompt(Long characterId) {
        return findCharacterOrThrow(characterId).getSystemPrompt();
    }

    private Conversation getOrCreateConversation(String deviceId, Long characterId) {
        User user = userRepository.findByDeviceId(deviceId)
                .orElseGet(() -> userRepository.save(new User(deviceId)));

        Character character = findCharacterOrThrow(characterId);

        return conversationRepository.findByUserIdAndCharacterId(user.getId(), character.getId())
                .orElseGet(() -> conversationRepository.save(new Conversation(user, character)));
    }

    private Character findCharacterOrThrow(Long characterId) {
        return characterRepository.findById(characterId)
                .orElseThrow(() -> new IllegalArgumentException("Character not found: " + characterId));
    }
}
