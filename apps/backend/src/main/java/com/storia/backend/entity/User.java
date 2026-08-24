package com.storia.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 익명 세션 유저. 로그인/회원가입 없이 클라이언트가 생성한 deviceId로만 식별된다.
 * 테이블명이 "user"가 아닌 "app_user"인 이유: MariaDB 예약어(USER)와 충돌 회피.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 클라이언트(AsyncStorage)가 생성해 X-Device-Id 헤더로 보내는 UUID. 사실상 로그인 ID 역할.
    @Column(nullable = false, unique = true)
    private String deviceId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // 채팅방 진입/메시지 전송마다 갱신됨 (ConversationService#getOrCreateConversation).
    // ReEngagementScheduler가 "3일 이상 활동 없음"을 판단하는 기준값.
    private Instant lastActiveAt = Instant.now();

    // PUT /api/devices/token으로 등록되는 FCM 토큰. null이면 푸시는 조용히 no-op.
    @Column(length = 512)
    private String fcmToken;

    // 재참여 푸시를 하루 한 번만 보내게 막는 플래그.
    // 발송 시 true(ReEngagementScheduler), 유저가 다시 활동하면 false로 리셋(getOrCreateConversation).
    @Column(nullable = false)
    private boolean reengagementPushSent = false;

    public User(String deviceId) {
        this.deviceId = deviceId;
    }
}
