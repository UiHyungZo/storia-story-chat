package com.storia.backend.repository;

import com.storia.backend.entity.Conversation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByUserIdAndCharacterId(Long userId, Long characterId);
}
