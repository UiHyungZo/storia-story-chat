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
 * 대화 상대가 되는 AI 캐릭터. CharacterSeeder가 앱 최초 기동 시 3종을 시딩한다.
 * 테이블명이 "character"가 아닌 "story_character"인 이유: MariaDB 예약어(CHARACTER)와 충돌 회피.
 */
@Entity
@Table(name = "story_character")
@Getter
@Setter
@NoArgsConstructor
public class Character {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // 목록 화면 등에 보여주는 한 줄 세계관 설명 (예: "우주선 항법 AI가 인격을 얻어가는 SF 세계관").
    @Column(nullable = false)
    private String concept;

    // Gemini 호출 시 systemInstruction으로 그대로 주입됨 (GeminiService#streamReply).
    // 이 캐릭터의 성격/말투를 정의하는 실질적인 핵심 필드.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    // TtsService#synthesize에 전달되는 Google Cloud TTS 보이스 이름 (예: ko-KR-Standard-A).
    // null/빈 값이면 TtsService가 TtsProperties#voiceName(전역 기본값)으로 폴백한다.
    private String ttsVoiceId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
