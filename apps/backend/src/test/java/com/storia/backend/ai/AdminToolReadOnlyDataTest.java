package com.storia.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.storia.backend.entity.Character;
import com.storia.backend.entity.Conversation;
import com.storia.backend.entity.Message;
import com.storia.backend.entity.User;
import com.storia.backend.repository.CharacterRepository;
import com.storia.backend.repository.ConversationRepository;
import com.storia.backend.repository.MessageRepository;
import com.storia.backend.repository.UserRepository;
import com.storia.backend.service.AdminConversationService;
import com.storia.backend.service.CharacterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/** 실제 H2 DB에서 도구 실행 전후 데이터가 바뀌지 않음을 확인한다 (운영 DB 미사용). */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AdminToolReadOnlyDataTest {

    @Autowired private CharacterRepository characterRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private MessageRepository messageRepository;

    @Test
    void toolExecution_doesNotChangeAnyRows() throws Exception {
        Character character = new Character();
        character.setName("루나");
        character.setConcept("컨셉");
        character.setSystemPrompt("프롬프트");
        character.setTtsVoiceId("ko-KR-Standard-A");
        characterRepository.save(character);
        User user = userRepository.save(new User("device-abcdef"));
        Conversation conversation = conversationRepository.save(new Conversation(user, character));
        messageRepository.save(new Message(conversation, Message.Role.USER, "안녕"));
        characterRepository.flush();

        AdminToolRegistry registry = new AdminToolRegistry(
                new CharacterService(characterRepository),
                new AdminConversationService(conversationRepository, messageRepository),
                new ObjectMapper());
        ObjectMapper mapper = new ObjectMapper();

        long users = userRepository.count();
        long conversations = conversationRepository.count();
        long messages = messageRepository.count();
        var lastActive = userRepository.findById(user.getId()).orElseThrow().getLastActiveAt();

        assertThat(registry.execute("getCharacterConfig",
                mapper.readTree("{\"characterId\":" + character.getId() + "}")).ok()).isTrue();
        assertThat(registry.execute("getConversationLogs", mapper.readTree("{\"characterId\":" + character.getId() + "}"))
                .payload().get("returned").asInt()).isEqualTo(1);
        assertThat(registry.execute("getConversationLogs",
                mapper.readTree("{\"conversationId\":" + conversation.getId() + "}")).payload().get("returned").asInt())
                .isEqualTo(1);
        assertThat(registry.execute("getCharacterConfig", mapper.readTree("{\"characterId\":9999}")).ok()).isFalse();
        characterRepository.flush();

        assertThat(userRepository.count()).isEqualTo(users);
        assertThat(conversationRepository.count()).isEqualTo(conversations);
        assertThat(messageRepository.count()).isEqualTo(messages);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getLastActiveAt()).isEqualTo(lastActive);
        Character after = characterRepository.findById(character.getId()).orElseThrow();
        assertThat(after.getSystemPrompt()).isEqualTo("프롬프트");
        assertThat(after.getConcept()).isEqualTo("컨셉");
        assertThat(after.getTtsVoiceId()).isEqualTo("ko-KR-Standard-A");
    }
}
