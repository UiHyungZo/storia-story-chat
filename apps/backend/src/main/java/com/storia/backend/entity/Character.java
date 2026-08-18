package com.storia.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Character {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String concept;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    private String avatarUrl;

    @Column(columnDefinition = "TEXT")
    private String worldviewMetadata;

    private String ttsVoiceId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
