package com.storia.backend.repository;

import com.storia.backend.entity.Message;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    /** 운영 콘솔 트랜스크립트 뷰용 페이지네이션 오버로드. 실시간 채팅 흐름은 위 List 버전을 그대로 쓴다. */
    Page<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId, Pageable pageable);
}
