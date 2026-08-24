package com.storia.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.storia.backend.entity.Character;
import com.storia.backend.entity.Conversation;
import com.storia.backend.entity.Message;
import com.storia.backend.entity.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * TODO.md 3주차 "DB 히스토리 저장/복원 검증"에서 코드 레벨로만 확인하고 실행 검증은
 * 미뤄뒀던 항목: MariaDB를 못 띄우는 환경에서도 H2 인메모리 DB로 오름차순 정렬 동작을
 * 실제로 검증한다.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class MessageRepositoryTest {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Test
    void findByConversationIdOrderByCreatedAtAsc_returnsMessagesOldestFirst() {
        Conversation conversation = persistConversation();

        Instant base = Instant.now();
        Message third = saveMessageAt(conversation, "third", base.plus(2, ChronoUnit.SECONDS));
        Message first = saveMessageAt(conversation, "first", base);
        Message second = saveMessageAt(conversation, "second", base.plus(1, ChronoUnit.SECONDS));

        List<Message> result = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());

        assertThat(result).extracting(Message::getContent).containsExactly("first", "second", "third");
        assertThat(result).extracting(Message::getId).containsExactly(first.getId(), second.getId(), third.getId());
    }

    @Test
    void findByConversationIdOrderByCreatedAtAsc_scopesToTheGivenConversation() {
        Conversation conversationA = persistConversation();
        Conversation conversationB = persistConversation();

        messageRepository.save(new Message(conversationA, Message.Role.USER, "in A"));
        messageRepository.save(new Message(conversationB, Message.Role.USER, "in B"));

        List<Message> resultA = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationA.getId());

        assertThat(resultA).extracting(Message::getContent).containsExactly("in A");
    }

    private Message saveMessageAt(Conversation conversation, String content, Instant createdAt) {
        Message message = new Message(conversation, Message.Role.USER, content);
        message.setCreatedAt(createdAt);
        return messageRepository.save(message);
    }

    private Conversation persistConversation() {
        User user = userRepository.save(new User("device-" + System.nanoTime()));
        Character character = new Character();
        character.setName("테스트 캐릭터");
        character.setConcept("concept");
        character.setSystemPrompt("prompt");
        character = characterRepository.save(character);
        return conversationRepository.save(new Conversation(user, character));
    }
}
