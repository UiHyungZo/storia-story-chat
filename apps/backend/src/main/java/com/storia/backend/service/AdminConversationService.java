package com.storia.backend.service;

import com.storia.backend.entity.Conversation;
import com.storia.backend.entity.Message;
import com.storia.backend.exception.ResourceNotFoundException;
import com.storia.backend.repository.ConversationRepository;
import com.storia.backend.repository.MessageRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * {@link ConversationService}는 device-scoped 실시간 채팅 흐름 전용이라, id 기반으로 전체를
 * 넘나드는 운영 콘솔 조회는 별도 서비스로 분리한다.
 */
@Service
@RequiredArgsConstructor
public class AdminConversationService {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Seoul");

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public Page<Conversation> search(Long characterId, String deviceId, LocalDate from, LocalDate to, Pageable pageable) {
        var fromInstant = from == null ? null : from.atStartOfDay(DISPLAY_ZONE).toInstant();
        // 종료일은 다음날 자정 미만(exclusive upper bound)으로 변환해 해당 날짜 전체를 포함시킨다.
        var toInstant = to == null ? null : to.plusDays(1).atStartOfDay(DISPLAY_ZONE).toInstant();
        return conversationRepository.search(characterId, deviceId, fromInstant, toInstant, pageable);
    }

    public Page<Message> getMessages(Long conversationId, Pageable pageable) {
        if (!conversationRepository.existsById(conversationId)) {
            throw new ResourceNotFoundException("Conversation not found: " + conversationId);
        }
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId, pageable);
    }
}
