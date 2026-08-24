package com.storia.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 유저-캐릭터 대화방. (user_id, character_id) 유니크 제약으로 "캐릭터당 대화 1개"만 허용한다
 * — 한 유저가 같은 캐릭터와 여러 대화 세션을 갖는 개념은 이 앱 범위 밖.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "character_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Conversation(User user, Character character) {
        this.user = user;
        this.character = character;
    }
}
