package com.storia.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 대화방 안의 메시지 한 건. createdAt 오름차순으로 조회되어(MessageRepository),
 * Gemini 호출 시 그대로 대화 히스토리(멀티턴 컨텍스트)로 재사용된다.
 */
@Entity
@Table(indexes = @Index(name = "idx_message_conversation_id", columnList = "conversation_id"))
@Getter
@Setter
@NoArgsConstructor
public class Message {

    public enum Role {
        USER,      // 유저가 보낸 메시지
        ASSISTANT  // Gemini가 생성한 응답
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Message(Conversation conversation, Role role, String content) {
        this.conversation = conversation;
        this.role = role;
        this.content = content;
    }
}
