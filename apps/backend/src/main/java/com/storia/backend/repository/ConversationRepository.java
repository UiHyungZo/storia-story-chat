package com.storia.backend.repository;

import com.storia.backend.entity.Conversation;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByUserIdAndCharacterId(Long userId, Long characterId);

    /**
     * 운영 콘솔용 다건 조회. user/character는 @ManyToOne(단일값)이라 JOIN FETCH를 Pageable과
     * 같이 써도 안전하다 — *ToMany 컬렉션을 fetch join할 때 생기는 "메모리에서 페이징" 문제와는
     * 다른 케이스.
     */
    @Query("""
            SELECT c FROM Conversation c
            JOIN FETCH c.user u
            JOIN FETCH c.character ch
            WHERE (:characterId IS NULL OR ch.id = :characterId)
              AND (:deviceId IS NULL OR u.deviceId = :deviceId)
              AND (:from IS NULL OR c.createdAt >= :from)
              AND (:to IS NULL OR c.createdAt < :to)
            """)
    Page<Conversation> search(
            @Param("characterId") Long characterId,
            @Param("deviceId") String deviceId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
