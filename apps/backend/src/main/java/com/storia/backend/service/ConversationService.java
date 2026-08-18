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

    @Transactional
    public List<Message> getMessages(String deviceId, Long characterId) {
        Conversation conversation = getOrCreateConversation(deviceId, characterId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
    }

    private Conversation getOrCreateConversation(String deviceId, Long characterId) {
        User user = userRepository.findByDeviceId(deviceId)
                .orElseGet(() -> userRepository.save(new User(deviceId)));

        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new IllegalArgumentException("Character not found: " + characterId));

        return conversationRepository.findByUserIdAndCharacterId(user.getId(), character.getId())
                .orElseGet(() -> conversationRepository.save(new Conversation(user, character)));
    }
}
